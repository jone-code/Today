package com.today.feedback;

import com.today.common.CreateIdempotencyService;
import com.today.common.RateLimitService;
import com.today.identity.IdentityService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeedbackService {
  private final JdbcTemplate jdbc;
  private final IdentityService identity;
  private final CreateIdempotencyService creates;
  private final RateLimitService rateLimits;
  private final int feedbackPerDay;

  public FeedbackService(JdbcTemplate jdbc, IdentityService identity,
      CreateIdempotencyService creates, RateLimitService rateLimits,
      @Value("${today.rate-limit.feedback-per-day:5}") int feedbackPerDay) {
    this.jdbc = jdbc;
    this.identity = identity;
    this.creates = creates;
    this.rateLimits = rateLimits;
    this.feedbackPerDay = feedbackPerDay;
  }

  @Transactional
  public FeedbackDto create(FeedbackCreateRequest input, String requestId) {
    String userId = identity.getCurrentUserId();
    FeedbackCreateRequest normalized = new FeedbackCreateRequest(input.kind(), input.body().trim());
    if (normalized.body().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feedback body is required");
    }
    FeedbackDto result = creates.create("feedback", requestId, normalized, FeedbackDto.class, () -> {
      rateLimits.check("feedback_day", userId, feedbackPerDay, Duration.ofDays(1));
      FeedbackDto report = new FeedbackDto(UUID.randomUUID().toString(), normalized.kind(),
          normalized.body(), Instant.now().toString());
      jdbc.update("INSERT INTO feedback_reports (id,user_id,kind,body,created_at) VALUES (?,?,?,?,?)",
          report.id(), userId, report.kind(), report.body(), java.sql.Timestamp.from(Instant.parse(report.createdAt())));
      return report;
    });
    Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM feedback_reports "
        + "WHERE id=? AND user_id=? AND deleted_at IS NULL", Integer.class, result.id(), userId);
    if (active == null || active == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "feedback request refers to a deleted report");
    }
    return result;
  }

  public List<FeedbackDto> list() {
    return jdbc.query("SELECT id,kind,body,created_at FROM feedback_reports "
            + "WHERE user_id=? AND deleted_at IS NULL ORDER BY created_at DESC,id DESC LIMIT 20",
        (rs, row) -> new FeedbackDto(rs.getString("id"), rs.getString("kind"),
            rs.getString("body"), rs.getTimestamp("created_at").toInstant().toString()),
        identity.getCurrentUserId());
  }

  @Transactional
  public void delete(String id) {
    String userId = identity.getCurrentUserId();
    if (jdbc.update("UPDATE feedback_reports SET deleted_at=NOW(3) "
        + "WHERE id=? AND user_id=? AND deleted_at IS NULL", id, userId) == 1) {
      // Keep the request key to prevent recreation, but remove deleted user text from its cached response.
      jdbc.update("UPDATE create_requests SET response_json=JSON_OBJECT('id', ?) "
          + "WHERE user_id=? AND scope='feedback' "
          + "AND JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.id'))=?", id, userId, id);
      return;
    }
    Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM feedback_reports WHERE id=? AND user_id=?",
        Integer.class, id, userId);
    if (owned == null || owned == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "feedback not found");
    }
  }
}
