package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class ProposalOptOutDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private ProposalService proposals;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;

  @Test
  void withdrawingConsentRejectsOnlyPendingProposalsInTheCurrentConversation() {
    String user = "optout-" + UUID.randomUUID();
    String other = "optout-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String otherConversation = UUID.randomUUID().toString();
    String currentMessage = UUID.randomUUID().toString();
    String memory = UUID.randomUUID().toString();
    String followup = UUID.randomUUID().toString();
    String unrelated = UUID.randomUUID().toString();
    String otherUserProposal = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversation(conversation, user);
      insertConversation(otherConversation, other);
      insertMessage(memory, conversation, user);
      insertMessage(followup, conversation, user);
      insertMessage(currentMessage, conversation, user);
      insertMessage(unrelated, otherConversation, other);
      insertMessage(otherUserProposal, otherConversation, other);
      insertProposal(memory, user, "memory", "pending");
      insertProposal(followup, user, "followup", "pending");
      insertProposal(unrelated, other, "memory", "pending");
      insertProposal(otherUserProposal, other, "followup", "pending");

      String optOut = "这件事不要记住，也别再跟进";
      proposals.withdrawPendingForOptOut(user, conversation, optOut);
      assertTrue(proposals.createCandidates(conversation, currentMessage,
          optOut, List.of(
              new ConversationProposal("memory", "life", "不应保存", null, null),
              new ConversationProposal("followup", null, null, "不应继续", 7))).isEmpty());

      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM agent_proposals "
          + "WHERE user_id=? AND status='rejected'", Integer.class, user));
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM agent_proposals "
          + "WHERE user_id=? AND status='pending'", Integer.class, other));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_proposals "
          + "WHERE source_message_id=?", Integer.class, currentMessage));
    } finally {
      jdbc.update("DELETE FROM agent_proposals WHERE user_id IN (?,?)", user, other);
      jdbc.update("DELETE FROM conversation_messages WHERE user_id IN (?,?)", user, other);
      jdbc.update("DELETE FROM conversations WHERE user_id IN (?,?)", user, other);
    }
  }

  @Test
  void rejectedFollowupReasonIsScopedToTheCurrentConversationAndUser() {
    String user = "followup-reject-" + UUID.randomUUID();
    String other = "followup-reject-" + UUID.randomUUID();
    String conversation = UUID.randomUUID().toString();
    String secondConversation = UUID.randomUUID().toString();
    String foreignConversation = UUID.randomUUID().toString();
    String rejectedMessage = UUID.randomUUID().toString();
    String currentMessage = UUID.randomUUID().toString();
    String secondMessage = UUID.randomUUID().toString();
    String foreignMessage = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      insertConversation(conversation, user);
      insertConversation(secondConversation, user);
      insertConversation(foreignConversation, other);
      insertMessage(rejectedMessage, conversation, user);
      insertMessage(currentMessage, conversation, user);
      insertMessage(secondMessage, secondConversation, user);
      insertMessage(foreignMessage, foreignConversation, other);
      insertRejectedFollowup(rejectedMessage, user, "日语学习进展");
      insertRejectedFollowup(foreignMessage, other, "跑步进展");

      assertTrue(proposals.createCandidates(conversation, currentMessage, "我还在学日语", List.of(
          new ConversationProposal("followup", null, null, "日语学习进展", 7))).isEmpty());
      assertEquals(1, proposals.createCandidates(conversation, currentMessage, "我还在跑步", List.of(
          new ConversationProposal("followup", null, null, "跑步进展", 7))).size());
      assertEquals(1, proposals.createCandidates(secondConversation, secondMessage, "我还在学日语", List.of(
          new ConversationProposal("followup", null, null, "日语学习进展", 7))).size());
    } finally {
      jdbc.update("DELETE FROM agent_proposals WHERE user_id IN (?,?)", user, other);
      jdbc.update("DELETE FROM conversation_messages WHERE user_id IN (?,?)", user, other);
      jdbc.update("DELETE FROM conversations WHERE user_id IN (?,?)", user, other);
    }
  }

  private void insertRejectedFollowup(String message, String user, String reason) {
    jdbc.update("INSERT INTO agent_proposals (id,user_id,source_message_id,kind,payload_json,status,created_at) "
        + "VALUES (?,?,?,'followup',JSON_OBJECT('reason',?),'rejected',NOW(3))",
        UUID.randomUUID().toString(), user, message, reason);
  }

  private void insertConversation(String id, String user) {
    jdbc.update("INSERT INTO conversations (id,user_id,status,version,created_at,updated_at) "
        + "VALUES (?,?,'active',1,NOW(3),NOW(3))", id, user);
  }

  private void insertMessage(String id, String conversation, String user) {
    jdbc.update("INSERT INTO conversation_messages (id,conversation_id,user_id,role,body,status,created_at) "
        + "VALUES (?,?,?,'user','test','succeeded',NOW(3))", id, conversation, user);
  }

  private void insertProposal(String message, String user, String kind, String status) {
    jdbc.update("INSERT INTO agent_proposals (id,user_id,source_message_id,kind,payload_json,status,created_at) "
        + "VALUES (?,?,?,?,'{}',?,NOW(3))", UUID.randomUUID().toString(), user, message, kind, status);
  }
}
