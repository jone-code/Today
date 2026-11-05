package com.today.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "mybatis.configuration.local-cache-scope=STATEMENT")
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class MemorySourceVisibilityDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private MemoryMapper memories;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @Transactional
  void onlyLiveOwnedSourcesAreVisibleAndAvailableToTheAgent() {
    String user = UUID.randomUUID().toString();
    String other = UUID.randomUUID().toString();
    String legacy = insertMemory(user);
    String checkinMemory = insertMemory(user);
    String checkin = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO checkins (id,user_id,checkin_date,raw_text,created_at,updated_at) "
        + "VALUES (?,?,CURRENT_DATE(),'record',NOW(3),NOW(3))", checkin, user);
    source(checkinMemory, user, "checkin", checkin);
    String foreignSource = insertMemory(user);
    source(foreignSource, other, "checkin", checkin);

    assertEquals(2, memories.listByUserId(user, false).size());
    assertEquals(1, memories.listConfirmedForAgent(user, 10).size());
    assertEquals(checkinMemory, memories.findConfirmedById(checkinMemory, user).getId());
    assertNull(memories.findConfirmedById(legacy, user));
    assertNull(memories.findVisibleById(foreignSource, user));

    jdbc.update("DELETE FROM checkins WHERE id=?", checkin);
    assertNull(memories.findVisibleById(checkinMemory, user));
    assertNull(memories.findConfirmedById(checkinMemory, user));
    assertEquals(1, memories.listByUserId(user, true).size());
    assertEquals(0, memories.listConfirmedForAgent(user, 10).size());

    String conversationMemory = insertMemory(user);
    String conversation = UUID.randomUUID().toString();
    String message = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO conversations (id,user_id,title,status,version,created_at,updated_at) "
        + "VALUES (?,?,'source','active',1,NOW(3),NOW(3))", conversation, user);
    jdbc.update("INSERT INTO conversation_messages (id,conversation_id,user_id,role,body,status,created_at) "
        + "VALUES (?,?,?,'user','source','succeeded',NOW(3))", message, conversation, user);
    source(conversationMemory, user, "conversation_message", message);
    assertEquals(conversationMemory, memories.findConfirmedById(conversationMemory, user).getId());

    jdbc.update("UPDATE conversations SET deleted_at=NOW(3) WHERE id=?", conversation);
    assertNull(memories.findVisibleById(conversationMemory, user));
    assertEquals(0, memories.listConfirmedForAgent(user, 10).size());
    jdbc.update("UPDATE conversations SET deleted_at=NULL WHERE id=?", conversation);
    jdbc.update("UPDATE conversation_messages SET deleted_at=NOW(3) WHERE id=?", message);
    assertNull(memories.findConfirmedById(conversationMemory, user));
  }

  @Test
  @Transactional
  void embeddingWriteRejectsDeletedOrEditedSource() {
    String user = UUID.randomUUID().toString();
    String memory = insertMemory(user);
    String checkin = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO checkins (id,user_id,checkin_date,raw_text,created_at,updated_at) "
        + "VALUES (?,?,CURRENT_DATE(),'record',NOW(3),NOW(3))", checkin, user);
    source(memory, user, "checkin", checkin);

    Instant original = memories.findConfirmedById(memory, user).getUpdatedAt();
    assertEquals(1, memories.updateEmbeddingIfConfirmed(memory, user, "test", original, "[1,0]", Instant.now()));
    assertTrue(memories.findConfirmedById(memory, user).getEmbeddingJson().contains("[1,0]"));

    jdbc.update("UPDATE memories SET memory_text='edited', embedding_json=NULL WHERE id=?", memory);
    assertEquals(0, memories.updateEmbeddingIfConfirmed(memory, user, "test", original, "[2,0]", Instant.now()));
    jdbc.update("DELETE FROM checkins WHERE id=?", checkin);
    Instant changed = jdbc.queryForObject("SELECT updated_at FROM memories WHERE id=?", java.sql.Timestamp.class,
        memory).toInstant();
    assertEquals(0, memories.updateEmbeddingIfConfirmed(memory, user, "edited", changed, "[2,0]", Instant.now()));
  }

  private String insertMemory(String user) {
    String id = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO memories (id,user_id,category,memory_text,strength,archived,updated_at) "
        + "VALUES (?,?,'life','test',1,0,NOW(3))", id, user);
    return id;
  }

  private void source(String memory, String user, String type, String id) {
    jdbc.update("INSERT INTO memory_sources (memory_id,user_id,source_type,source_id,active,created_at) "
        + "VALUES (?,?,?,?,1,NOW(3))", memory, user, type, id);
  }
}
