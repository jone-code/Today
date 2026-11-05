package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "today.rate-limit.conversation-create-per-day=1")
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class ConversationCreateLimitDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private ConversationService conversations;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;
  @Value("${today.jwt.secret}") private String pepper;

  @Test
  void replayDoesNotConsumeQuotaAndRejectedCreationRollsBack() throws Exception {
    String user = "create-limit-" + UUID.randomUUID();
    when(identity.getCurrentUserId()).thenReturn(user);
    String subjectHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest((pepper + ":conversation_create_day:" + user).getBytes(StandardCharsets.UTF_8)));
    try {
      String created = conversations.create(new ConversationCreateRequest(null, "key-one")).id();
      assertEquals(created, conversations.create(new ConversationCreateRequest(null, "key-one")).id());
      assertEquals(1, jdbc.queryForObject("SELECT hit_count FROM api_rate_limits "
          + "WHERE scope='conversation_create_day' AND subject_hash=?", Integer.class, subjectHash));

      ResponseStatusException error = assertThrows(ResponseStatusException.class,
          () -> conversations.create(new ConversationCreateRequest(null, "key-two")));

      assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE user_id=?",
          Integer.class, user));
    } finally {
      jdbc.update("DELETE FROM conversations WHERE user_id=?", user);
      jdbc.update("DELETE FROM api_rate_limits WHERE scope='conversation_create_day' AND subject_hash=?",
          subjectHash);
    }
  }
}
