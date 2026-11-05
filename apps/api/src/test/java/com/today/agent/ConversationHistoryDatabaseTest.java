package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class ConversationHistoryDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private ConversationMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void retryHistoryExcludesCurrentAndLaterMessagesAndOtherUsers() {
    String user = "history-" + UUID.randomUUID();
    String otherUser = "history-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    Instant cutoff = Instant.parse("2026-11-03T12:00:00Z");
    try {
      jdbc.update("INSERT INTO conversations (id,user_id,title,status,version,created_at,updated_at) "
          + "VALUES (?,?,'test','active',1,NOW(3),NOW(3))", conversation, user);
      insert(conversation, user, "before", cutoff.minusSeconds(1));
      insert(conversation, otherUser, "other user", cutoff.minusSeconds(1));
      insert(conversation, user, "current", cutoff);
      insert(conversation, user, "later", cutoff.plusSeconds(1));

      var history = mapper.listHistoryBefore(conversation, user, cutoff, 20);

      assertEquals(1, history.size());
      assertEquals("before", history.get(0).getBody());
    } finally {
      jdbc.update("DELETE FROM conversation_messages WHERE conversation_id = ?", conversation);
      jdbc.update("DELETE FROM conversations WHERE id = ?", conversation);
    }
  }

  @Test
  void messagePagesDoNotSkipOrRepeatMessagesWithTheSameTimestamp() {
    String user = "page-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    Instant timestamp = Instant.parse("2026-11-03T12:00:00.123Z");
    try {
      jdbc.update("INSERT INTO conversations (id,user_id,title,status,version,created_at,updated_at) "
          + "VALUES (?,?,'test','active',1,NOW(3),NOW(3))", conversation, user);
      List<String> ids = List.of(
          insert(conversation, user, "first", timestamp),
          insert(conversation, user, "second", timestamp),
          insert(conversation, user, "third", timestamp),
          insert(conversation, user, "fourth", timestamp));
      insert(conversation, "other-user", "hidden", timestamp);
      String deleted = insert(conversation, user, "deleted", timestamp);
      jdbc.update("UPDATE conversation_messages SET deleted_at=NOW(3) WHERE id=?", deleted);

      var first = mapper.listMessages(conversation, user, 2);
      var second = mapper.listMessagesBefore(conversation, user, timestamp, first.get(1).getId(), 3);
      List<String> actual = java.util.stream.Stream.concat(first.stream(), second.stream())
          .map(message -> message.getId()).toList();
      List<String> expected = ids.stream().sorted(Comparator.reverseOrder()).toList();

      assertEquals(expected, actual);
    } finally {
      jdbc.update("DELETE FROM conversation_messages WHERE conversation_id=?", conversation);
      jdbc.update("DELETE FROM conversations WHERE id=?", conversation);
    }
  }

  private String insert(String conversation, String user, String body, Instant createdAt) {
    String id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO conversation_messages "
        + "(id,conversation_id,user_id,role,body,status,created_at) "
        + "VALUES (?,?,?,'user',?,'succeeded',?)",
        id, conversation, user, body, Timestamp.from(createdAt));
    return id;
  }
}
