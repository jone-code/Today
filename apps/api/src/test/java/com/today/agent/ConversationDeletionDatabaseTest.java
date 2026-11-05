package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.memory.MemoryMapper;
import java.util.List;
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
class ConversationDeletionDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private ConversationService conversations;
  @Autowired private ConversationMessageStore messages;
  @Autowired private ProposalService proposals;
  @Autowired private MemoryMapper memories;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;

  @Test
  void deleteRevokesDerivedDataAndRejectsLateModelCompletion() {
    String user = "delete-test-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String message = UUID.randomUUID().toString();
    String memory = UUID.randomUUID().toString();
    String proposal = UUID.randomUUID().toString();
    String topic = UUID.randomUUID().toString();
    String inbox = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      jdbc.update("INSERT INTO conversations (id,user_id,title,status,version,created_at,updated_at) "
          + "VALUES (?,?,'test','active',1,NOW(3),NOW(3))", conversation, user);
      jdbc.update("INSERT INTO conversation_messages "
          + "(id,conversation_id,user_id,role,body,client_message_id,status,generation_attempt,created_at) "
          + "VALUES (?,?,?,'user','original private text','request-1','processing',1,NOW(3))",
          message, conversation, user);
      jdbc.update("INSERT INTO memories (id,user_id,category,memory_text,strength,archived,updated_at) "
          + "VALUES (?,?,'fact','derived private memory',1,0,NOW(3))", memory, user);
      jdbc.update("INSERT INTO memory_sources (memory_id,user_id,source_type,source_id,active,created_at) "
          + "VALUES (?,?,'conversation_message',?,1,NOW(3))", memory, user, message);
      jdbc.update("INSERT INTO agent_proposals (id,user_id,source_message_id,kind,payload_json,status,created_at) "
          + "VALUES (?,?,?,'memory','{}','pending',NOW(3))", proposal, user, message);
      jdbc.update("INSERT INTO followup_topics (id,user_id,source_conversation_id,source_message_id,reason,state,"
          + "next_eligible_at,created_at,updated_at) "
          + "VALUES (?,?,?,?,'private reason','authorized',NOW(3),NOW(3),NOW(3))",
          topic, user, conversation, message);
      jdbc.update("INSERT INTO inbox_items "
          + "(id,user_id,kind,source_kind,source_id,body,scheduled_at,state,created_at) "
          + "VALUES (?,?,'followup','followup_topic',?,'private prompt',NOW(3),'unread',NOW(3))",
          inbox, user, topic);

      conversations.delete(conversation);

      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE id=? AND deleted_at IS NOT NULL",
          Integer.class, conversation));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages "
          + "WHERE id=? AND deleted_at IS NOT NULL", Integer.class, message));
      assertEquals("rejected", jdbc.queryForObject("SELECT status FROM agent_proposals WHERE id=?",
          String.class, proposal));
      assertEquals(0, jdbc.queryForObject("SELECT active FROM memory_sources WHERE memory_id=?",
          Integer.class, memory));
      assertEquals(1, jdbc.queryForObject("SELECT archived FROM memories WHERE id=?",
          Integer.class, memory));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vector_sync_jobs "
          + "WHERE memory_id=? AND user_id=?", Integer.class, memory, user));
      assertNull(memories.findConfirmedById(memory, user));
      assertEquals(List.of(), memories.listByUserId(user, true));
      assertEquals("deleted", jdbc.queryForObject("SELECT state FROM followup_topics WHERE id=?",
          String.class, topic));
      assertEquals("dismissed", jdbc.queryForObject("SELECT state FROM inbox_items WHERE id=?",
          String.class, inbox));
      assertEquals("来源对话已删除", jdbc.queryForObject("SELECT body FROM inbox_items WHERE id=?",
          String.class, inbox));
      assertThrows(ResponseStatusException.class, () -> messages.finish(conversation, user, "request-1", 1,
          "late reply", "llm", List.of()));
      assertThrows(ResponseStatusException.class, () -> proposals.confirm(proposal));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id=?",
          Integer.class, conversation));
    } finally {
      jdbc.update("DELETE FROM vector_sync_jobs WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM inbox_items WHERE id=?", inbox);
      jdbc.update("DELETE FROM followup_topics WHERE id=?", topic);
      jdbc.update("DELETE FROM agent_proposals WHERE id=?", proposal);
      jdbc.update("DELETE FROM memory_sources WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM memories WHERE id=?", memory);
      jdbc.update("DELETE FROM conversation_messages WHERE conversation_id=?", conversation);
      jdbc.update("DELETE FROM conversations WHERE id=?", conversation);
    }
  }
}
