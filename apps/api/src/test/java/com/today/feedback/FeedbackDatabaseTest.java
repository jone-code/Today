package com.today.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class FeedbackDatabaseTest {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
    registry.add("today.rate-limit.feedback-per-day", () -> 2);
  }

  @Autowired private FeedbackService feedback;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;

  @Test
  void feedbackIsIdempotentOwnedDeletableAndRateLimited() {
    String user = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    try {
      when(identity.getCurrentUserId()).thenReturn(user);
      FeedbackDto first = feedback.create(new FeedbackCreateRequest("issue", "  页面打不开  "), requestId);
      assertEquals("页面打不开", first.body());
      assertEquals(first, feedback.create(new FeedbackCreateRequest("issue", "页面打不开"), requestId));
      assertEquals(1, feedback.list().size());
      assertEquals(409, assertThrows(ResponseStatusException.class, () ->
          feedback.create(new FeedbackCreateRequest("issue", "其他问题"), requestId)).getStatusCode().value());

      when(identity.getCurrentUserId()).thenReturn(other);
      assertEquals(0, feedback.list().size());
      assertEquals(404, assertThrows(ResponseStatusException.class, () ->
          feedback.delete(first.id())).getStatusCode().value());
      FeedbackDto another = feedback.create(new FeedbackCreateRequest("idea", "希望可以筛选"), requestId);
      assertNotEquals(first.id(), another.id());

      when(identity.getCurrentUserId()).thenReturn(user);
      feedback.delete(first.id());
      feedback.delete(first.id());
      assertEquals(0, feedback.list().size());
      String stored = jdbc.queryForObject("SELECT response_json FROM create_requests "
          + "WHERE user_id=? AND scope='feedback' AND request_id=?", String.class, user, requestId);
      assertFalse(stored.contains("页面打不开"));
      assertEquals(first.id(), jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(response_json, '$.id')) "
          + "FROM create_requests WHERE user_id=? AND scope='feedback' AND request_id=?",
          String.class, user, requestId));
      assertEquals(409, assertThrows(ResponseStatusException.class, () ->
          feedback.create(new FeedbackCreateRequest("issue", "页面打不开"), requestId)).getStatusCode().value());
      assertEquals(0, feedback.list().size());
      feedback.create(new FeedbackCreateRequest("idea", "希望可以搜索"), UUID.randomUUID().toString());
      assertEquals(429, assertThrows(ResponseStatusException.class, () ->
          feedback.create(new FeedbackCreateRequest("issue", "又一个问题"), UUID.randomUUID().toString()))
          .getStatusCode().value());
      assertEquals(1, feedback.list().size());
    } finally {
      jdbc.update("DELETE FROM feedback_reports WHERE user_id IN (?,?)", user, other);
      jdbc.update("DELETE FROM create_requests WHERE user_id IN (?,?) AND scope='feedback'", user, other);
    }
  }
}
