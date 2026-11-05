package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class FollowupLifecycleDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private FollowupService followups;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;

  @Test
  void secondUnreadDeliveryPausesAndCanBeContinuedFromInbox() {
    String user = "followup-lifecycle-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    Instant due = Instant.now().plus(Duration.ofDays(30));
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 2, due.minusSeconds(60));
      insertInbox(user, topic, inbox, due.minus(Duration.ofDays(7)), "unread");

      assertEquals(0, followups.dispatchDue(due));
      assertEquals("paused", jdbc.queryForObject("SELECT state FROM followup_topics WHERE id = ?", String.class, topic));
      assertEquals(conversation, followups.inbox(20).get(0).conversationId());
      assertEquals(conversation, followups.continueConversation(inbox).conversationId());
      assertEquals("authorized", jdbc.queryForObject("SELECT state FROM followup_topics WHERE id = ?", String.class, topic));
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?", Integer.class, topic));
      assertEquals("handled", jdbc.queryForObject("SELECT state FROM inbox_items WHERE id = ?", String.class, inbox));
      assertEquals(conversation, followups.continueConversation(inbox).conversationId());
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?", Integer.class, topic));
    } finally {
      remove(user, conversation);
    }
  }

  @Test
  void snoozeAcknowledgesAnAuthorizedTopicWithoutCreatingAnotherMessage() {
    String user = "followup-snooze-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 1, Instant.now().plus(Duration.ofDays(7)));
      insertInbox(user, topic, inbox, Instant.now(), "unread");
      followups.snooze(inbox, Instant.now().plus(Duration.ofDays(1)));
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?", Integer.class, topic));
      assertEquals("snoozed", jdbc.queryForObject("SELECT state FROM inbox_items WHERE id = ?", String.class, inbox));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inbox_items WHERE user_id = ?", Integer.class, user));
    } finally {
      remove(user, conversation);
    }
  }

  @Test
  void snoozeDateIsStoredAtNineInTheUsersConfiguredTimezone() {
    String user = "followup-zone-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    ZoneId zone = ZoneId.of("Pacific/Honolulu");
    LocalDate selected = LocalDate.now(zone).plusDays(1);
    Instant expected = selected.atTime(9, 0).atZone(zone).toInstant();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 1, Instant.now().plus(Duration.ofDays(7)));
      insertInbox(user, topic, inbox, Instant.now(), "unread");
      followups.updatePreferences(new FollowupPreferenceUpdateRequest(true, 1, 3, zone.getId()));

      assertEquals(expected.toString(), followups.snoozeOnDate(inbox, selected.toString()).snoozedUntil());
      assertEquals(expected, jdbc.queryForObject("SELECT snoozed_until FROM inbox_items WHERE id = ?",
          java.sql.Timestamp.class, inbox).toInstant());
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?",
          Integer.class, topic));
    } finally {
      remove(user, conversation);
      jdbc.update("DELETE FROM followup_preferences WHERE user_id = ?", user);
    }
  }

  @Test
  void stoppingAutoPausedTopicDismissesInboxAndPreventsContinuing() {
    String user = "followup-stop-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    Instant due = Instant.now().plus(Duration.ofDays(30));
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 2, due.minusSeconds(60));
      insertInbox(user, topic, inbox, due.minus(Duration.ofDays(7)), "unread");
      followups.dispatchDue(due);

      followups.stopTopic(topic);
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?", Integer.class, topic));
      assertEquals("dismissed", jdbc.queryForObject("SELECT state FROM inbox_items WHERE id = ?", String.class, inbox));
      assertThrows(ResponseStatusException.class, () -> followups.continueConversation(inbox));
    } finally {
      remove(user, conversation);
    }
  }

  @Test
  void handledInboxCannotBeContinuedAfterTopicIsStopped() {
    String user = "followup-retry-stop-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 1, Instant.now().plus(Duration.ofDays(7)));
      insertInbox(user, topic, inbox, Instant.now(), "unread");
      assertEquals(conversation, followups.continueConversation(inbox).conversationId());
      followups.stopTopic(topic);
      assertThrows(ResponseStatusException.class, () -> followups.continueConversation(inbox));
    } finally {
      remove(user, conversation);
    }
  }

  @Test
  void twoConcurrentContinuesReturnTheSameConversation() throws Exception {
    String user = "followup-race-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    var pool = Executors.newFixedThreadPool(2);
    try {
      insertConversationAndTopic(user, conversation, topic, 1, Instant.now().plus(Duration.ofDays(7)));
      insertInbox(user, topic, inbox, Instant.now(), "unread");
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      var task = (java.util.concurrent.Callable<InboxItemDto>) () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("not ready");
        return followups.continueConversation(inbox);
      };
      var first = pool.submit(task);
      var second = pool.submit(task);
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      assertEquals(conversation, first.get(20, TimeUnit.SECONDS).conversationId());
      assertEquals(conversation, second.get(20, TimeUnit.SECONDS).conversationId());
      assertEquals("handled", jdbc.queryForObject("SELECT state FROM inbox_items WHERE id = ?", String.class, inbox));
      assertEquals(0, jdbc.queryForObject("SELECT unanswered_count FROM followup_topics WHERE id = ?", Integer.class, topic));
    } finally {
      pool.shutdownNow();
      remove(user, conversation);
    }
  }

  @Test
  void appointmentContinueCanBeRetriedButNotAfterSourceDeletion() {
    String user = "appointment-continue-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String appointment = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      jdbc.update("INSERT INTO conversations (id,user_id,status,version,created_at,updated_at) "
          + "VALUES (?,?,'active',1,NOW(3),NOW(3))", conversation, user);
      jdbc.update("INSERT INTO one_off_appointments (id,user_id,proposal_id,source_conversation_id,"
          + "source_message_id,title,due_at_utc,original_timezone,status,created_at,delivered_at) "
          + "VALUES (?,?,?,?,?,'test',NOW(3),'Asia/Shanghai','delivered',NOW(3),NOW(3))",
          appointment, user, UUID.randomUUID().toString(), conversation, UUID.randomUUID().toString());
      jdbc.update("INSERT INTO inbox_items (id,user_id,kind,source_kind,source_id,body,scheduled_at,state,created_at) "
          + "VALUES (?,?,'appointment','one_off_appointment',?,'test',NOW(3),'unread',NOW(3))",
          inbox, user, appointment);
      assertEquals(conversation, followups.continueConversation(inbox).conversationId());
      assertEquals(conversation, followups.continueConversation(inbox).conversationId());
      jdbc.update("UPDATE one_off_appointments SET status='deleted' WHERE id=?", appointment);
      assertThrows(ResponseStatusException.class, () -> followups.continueConversation(inbox));
    } finally {
      jdbc.update("DELETE FROM inbox_items WHERE user_id=?", user);
      jdbc.update("DELETE FROM one_off_appointments WHERE user_id=?", user);
      jdbc.update("DELETE FROM conversations WHERE id=?", conversation);
    }
  }

  @Test
  void readAndDismissRetriesKeepTheirStateWithoutExposingOtherAccounts() {
    String user = "inbox-retry-" + UUID.randomUUID();
    String other = "inbox-other-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversationAndTopic(user, conversation, topic, 0, Instant.now().plus(Duration.ofDays(7)));
      insertInbox(user, topic, inbox, Instant.now(), "unread");

      assertEquals("read", followups.read(inbox).state());
      jdbc.update("UPDATE inbox_items SET read_at=? WHERE id=?",
          Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), inbox);
      Timestamp firstReadAt = jdbc.queryForObject("SELECT read_at FROM inbox_items WHERE id=?",
          Timestamp.class, inbox);
      assertEquals("read", followups.read(inbox).state());
      assertEquals(firstReadAt, jdbc.queryForObject("SELECT read_at FROM inbox_items WHERE id=?",
          Timestamp.class, inbox));

      assertEquals("dismissed", followups.dismiss(inbox).state());
      assertEquals("dismissed", followups.dismiss(inbox).state());
      assertThrows(ResponseStatusException.class, () -> followups.read(inbox));
      when(identity.getCurrentUserId()).thenReturn(other);
      assertThrows(ResponseStatusException.class, () -> followups.dismiss(inbox));
    } finally {
      remove(user, conversation);
    }
  }

  private void insertConversationAndTopic(String user, String conversation, String topic, int unanswered, Instant due) {
    jdbc.update("INSERT INTO conversations (id, user_id, title, status, version, created_at, updated_at) "
        + "VALUES (?, ?, 'test', 'active', 1, NOW(3), NOW(3))", conversation, user);
    jdbc.update("INSERT INTO followup_topics (id, user_id, source_conversation_id, source_message_id, "
        + "reason, state, next_eligible_at, unanswered_count, last_delivered_at, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, 'test', 'authorized', ?, ?, ?, NOW(3), NOW(3))",
        topic, user, conversation, UUID.randomUUID().toString(), Timestamp.from(due),
        unanswered, Timestamp.from(due.minus(Duration.ofDays(7))));
  }

  private void insertInbox(String user, String topic, String inbox, Instant scheduled, String state) {
    jdbc.update("INSERT INTO inbox_items (id, user_id, kind, source_kind, source_id, body, scheduled_at, state, created_at) "
        + "VALUES (?, ?, 'followup', 'followup_topic', ?, 'test', ?, ?, NOW(3))",
        inbox, user, topic, Timestamp.from(scheduled), state);
  }

  private void remove(String user, String conversation) {
    jdbc.update("DELETE FROM inbox_items WHERE user_id = ?", user);
    jdbc.update("DELETE FROM followup_topics WHERE user_id = ?", user);
    jdbc.update("DELETE FROM conversations WHERE id = ?", conversation);
  }
}
