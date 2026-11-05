package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class FollowupConcurrencyDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private FollowupService followups;
  @Autowired private FollowupMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void unreadCountIsAccountScopedAndWakesOnlyDueMessages() {
    String firstUser = "inbox-count-first-" + UUID.randomUUID();
    String otherUser = "inbox-count-other-" + UUID.randomUUID();
    Instant now = Instant.now();
    try {
      insertInbox(firstUser, "unread", now, null);
      insertInbox(firstUser, "snoozed", now, now.minusSeconds(60));
      insertInbox(firstUser, "snoozed", now, now.plusSeconds(3600));
      insertInbox(firstUser, "read", now, null);
      insertInbox(otherUser, "unread", now, null);

      mapper.wakeSnoozed(firstUser, now);

      assertEquals(2, mapper.countUnread(firstUser));
      assertEquals(1, mapper.countUnread(otherUser));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inbox_items WHERE user_id=? AND state='snoozed'",
          Integer.class, firstUser));
    } finally {
      jdbc.update("DELETE FROM inbox_items WHERE user_id IN (?, ?)", firstUser, otherUser);
    }
  }

  private void insertInbox(String user, String state, Instant scheduled, Instant snoozedUntil) {
    jdbc.update("INSERT INTO inbox_items (id,user_id,kind,source_kind,source_id,body,scheduled_at,state,created_at,snoozed_until) "
            + "VALUES (?,?, 'followup','followup_topic',?,'test',?,?,NOW(3),?)",
        UUID.randomUUID().toString(), user, UUID.randomUUID().toString(), java.sql.Timestamp.from(scheduled), state,
        snoozedUntil == null ? null : java.sql.Timestamp.from(snoozedUntil));
  }

  @Test
  void disabledTopicsDoNotStarveOtherUsersBeyondTheScanLimit() {
    String disabledUser = "followup-disabled-" + UUID.randomUUID();
    String activeUser = "followup-active-" + UUID.randomUUID();
    Instant now = Instant.now().plus(Duration.ofDays(30));
    try {
      jdbc.update("INSERT INTO followup_preferences (user_id, timezone, max_per_day, max_per_week, enabled, updated_at) "
          + "VALUES (?, 'Asia/Shanghai', 1, 3, 0, NOW(3))", disabledUser);
      jdbc.update("INSERT INTO followup_preferences (user_id, timezone, max_per_day, max_per_week, enabled, updated_at) "
          + "VALUES (?, 'Asia/Shanghai', 1, 3, 1, NOW(3))", activeUser);
      for (int i = 0; i < 100; i++) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO followup_topics (id, user_id, source_conversation_id, source_message_id, "
            + "reason, state, next_eligible_at, unanswered_count, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, 'disabled test', 'authorized', ?, 0, NOW(3), NOW(3))",
            id, disabledUser, id, id, java.sql.Timestamp.from(now.minusSeconds(120)));
      }
      String activeId = UUID.randomUUID().toString();
      jdbc.update("INSERT INTO followup_topics (id, user_id, source_conversation_id, source_message_id, "
          + "reason, state, next_eligible_at, unanswered_count, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, 'active test', 'authorized', ?, 0, NOW(3), NOW(3))",
          activeId, activeUser, activeId, activeId, java.sql.Timestamp.from(now.minusSeconds(60)));

      assertEquals(0, followups.dispatchDue(now));
      assertEquals(100, jdbc.queryForObject(
          "SELECT COUNT(*) FROM followup_topics WHERE user_id = ? AND next_eligible_at > ?",
          Integer.class, disabledUser, java.sql.Timestamp.from(now)));
      assertEquals(1, followups.dispatchDue(now));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inbox_items WHERE user_id = ?",
          Integer.class, disabledUser));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inbox_items WHERE user_id = ?",
          Integer.class, activeUser));
    } finally {
      for (String user : new String[] {disabledUser, activeUser}) {
        jdbc.update("DELETE FROM inbox_items WHERE user_id = ?", user);
        jdbc.update("DELETE FROM followup_topics WHERE user_id = ?", user);
        jdbc.update("DELETE FROM followup_preferences WHERE user_id = ?", user);
      }
    }
  }

  @Test
  void twoWorkersDoNotExceedOneDeliveryPerUserDay() throws Exception {
    String user = "followup-concurrency-" + UUID.randomUUID();
    Instant now = Instant.now().plus(Duration.ofDays(30));
    var pool = Executors.newFixedThreadPool(2);
    try {
      jdbc.update("INSERT INTO followup_preferences (user_id, timezone, max_per_day, max_per_week, enabled, updated_at) "
          + "VALUES (?, 'Asia/Shanghai', 1, 3, 1, NOW(3))", user);
      for (int i = 0; i < 3; i++) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO followup_topics (id, user_id, source_conversation_id, source_message_id, "
            + "reason, state, next_eligible_at, unanswered_count, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, 'followup test', 'authorized', ?, 0, NOW(3), NOW(3))",
            id, user, id, id, java.sql.Timestamp.from(now.minusSeconds(60)));
      }
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      var task = (java.util.concurrent.Callable<Integer>) () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("workers did not start");
        return followups.dispatchDue(now);
      };
      var first = pool.submit(task);
      var second = pool.submit(task);
      if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("workers not ready");
      start.countDown();
      assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
      assertEquals(1, jdbc.queryForObject(
          "SELECT COUNT(*) FROM inbox_items WHERE user_id = ? AND kind = 'followup'", Integer.class, user));
    } finally {
      pool.shutdownNow();
      jdbc.update("DELETE FROM inbox_items WHERE user_id = ?", user);
      jdbc.update("DELETE FROM followup_topics WHERE user_id = ?", user);
      jdbc.update("DELETE FROM followup_preferences WHERE user_id = ?", user);
    }
  }
}
