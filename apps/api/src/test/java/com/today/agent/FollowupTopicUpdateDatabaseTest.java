package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class FollowupTopicUpdateDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private FollowupService followups;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;

  @Test
  void editingUsesAccountTimezoneAndRejectsStaleOrForeignRequests() {
    String user = "topic-edit-" + UUID.randomUUID();
    String other = "topic-other-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String id = UUID.randomUUID().toString();
    ZoneId zone = ZoneId.of("Pacific/Honolulu");
    LocalDate selected = LocalDate.now(zone).plusDays(3);
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertTopic(user, conversation, id, null);
      followups.updatePreferences(new FollowupPreferenceUpdateRequest(true, 1, 3, zone.getId()));
      FollowupTopicDto initial = followups.topics(10).get(0);
      var request = new FollowupTopicUpdateRequest(" 面试结果 ", selected.toString(),
          initial.reason(), initial.nextEligibleAt());

      FollowupTopicDto updated = followups.updateTopic(id, request);
      assertEquals("面试结果", updated.reason());
      assertEquals(selected.atTime(9, 0).atZone(zone).toInstant(),
          jdbc.queryForObject("SELECT next_eligible_at FROM followup_topics WHERE id=?",
              Timestamp.class, id).toInstant());
      assertEquals(updated, followups.updateTopic(id, request));
      assertEquals(409, assertThrows(ResponseStatusException.class, () -> followups.updateTopic(id,
          new FollowupTopicUpdateRequest("另一件事", null, initial.reason(), initial.nextEligibleAt())))
              .getStatusCode().value());

      when(identity.getCurrentUserId()).thenReturn(other);
      assertEquals(404, assertThrows(ResponseStatusException.class, () -> followups.updateTopic(id, request))
          .getStatusCode().value());
      when(identity.getCurrentUserId()).thenReturn(user);
      jdbc.update("UPDATE conversations SET deleted_at=NOW(3) WHERE id=?", conversation);
      assertEquals(404, assertThrows(ResponseStatusException.class, () -> followups.updateTopic(id, request))
          .getStatusCode().value());
    } finally {
      remove(user, conversation);
    }
  }

  @Test
  void editingNeverShortensSevenDayGapOrReactivatesPausedTopics() {
    String user = "topic-gap-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String id = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertTopic(user, conversation, id, Instant.now().minus(Duration.ofDays(1)));
      FollowupTopicDto initial = followups.topics(10).get(0);
      var early = new FollowupTopicUpdateRequest("面试", LocalDate.now().plusDays(1).toString(),
          initial.reason(), initial.nextEligibleAt());
      assertEquals(409, assertThrows(ResponseStatusException.class, () -> followups.updateTopic(id, early))
          .getStatusCode().value());
      jdbc.update("UPDATE followup_topics SET state='paused' WHERE id=?", id);
      assertEquals(404, assertThrows(ResponseStatusException.class, () -> followups.updateTopic(id,
          new FollowupTopicUpdateRequest("面试", null, initial.reason(), initial.nextEligibleAt())))
              .getStatusCode().value());
    } finally {
      remove(user, conversation);
    }
  }

  private void insertTopic(String user, String conversation, String id, Instant lastDelivered) {
    jdbc.update("INSERT INTO conversations (id,user_id,status,version,created_at,updated_at) "
        + "VALUES (?,?,'active',1,NOW(3),NOW(3))", conversation, user);
    jdbc.update("INSERT INTO followup_topics (id,user_id,source_conversation_id,source_message_id,reason,state,"
        + "next_eligible_at,unanswered_count,last_delivered_at,created_at,updated_at) "
        + "VALUES (?,?,?,?,'准备面试','authorized',?,0,?,NOW(3),NOW(3))",
        id, user, conversation, UUID.randomUUID().toString(),
        Timestamp.from(Instant.now().plus(Duration.ofDays(10))),
        lastDelivered == null ? null : Timestamp.from(lastDelivered));
  }

  private void remove(String user, String conversation) {
    jdbc.update("DELETE FROM followup_topics WHERE user_id=?", user);
    jdbc.update("DELETE FROM followup_preferences WHERE user_id=?", user);
    jdbc.update("DELETE FROM conversations WHERE id=?", conversation);
  }
}
