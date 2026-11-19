package com.today.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.today.agent.FollowupService;
import com.today.agent.FollowupPreferenceUpdateRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class FollowupPrivacyConsistencyDatabaseTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private FollowupService followups;
  @Autowired private PrivacySettingsService privacy;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private IdentityService identity;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void dedicatedAndPrivacyEndpointsKeepFollowupAuthorizationInSync() {
    String user = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      jdbc.update("INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at) VALUES(?,?, 'test','test',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", user, user + "@example.test");
      followups.updatePreferences(new FollowupPreferenceUpdateRequest(false, 1, 3, "Asia/Shanghai"));
      assertEquals(0, scalar("SELECT enabled FROM followup_preferences WHERE user_id=?", user));
      assertEquals(0, scalar("SELECT followup_enabled FROM user_privacy_settings WHERE user_id=?", user));

      PrivacySettingsDto current = privacy.get();
      PrivacySettingsRequest request = new PrivacySettingsRequest();
      request.setVersion(current.version());
      request.setChatMemoryEnabled(current.chatMemoryEnabled());
      request.setVoiceRetention(current.voiceRetention());
      request.setFollowupEnabled(true);
      privacy.update(request);
      assertEquals(1, scalar("SELECT enabled FROM followup_preferences WHERE user_id=?", user));
      assertEquals(1, scalar("SELECT followup_enabled FROM user_privacy_settings WHERE user_id=?", user));
      assertEquals(2, scalar("SELECT COUNT(*) FROM privacy_settings_audit WHERE user_id=? AND field='followup_enabled'", user));
      assertEquals(1, scalar("SELECT COUNT(*) FROM privacy_settings_audit WHERE user_id=? AND source='followup_preferences'", user));
      assertEquals(1, scalar("SELECT COUNT(*) FROM privacy_settings_audit WHERE user_id=? AND source='profile_privacy'", user));
    } finally {
      jdbc.update("DELETE FROM privacy_settings_audit WHERE user_id=?", user);
      jdbc.update("DELETE FROM user_privacy_settings WHERE user_id=?", user);
      jdbc.update("DELETE FROM followup_preferences WHERE user_id=?", user);
      jdbc.update("DELETE FROM users WHERE id=?", user);
    }
  }

  private int scalar(String sql, String user) {
    return jdbc.queryForObject(sql, Integer.class, user);
  }

  @Test
  void settingsRequestsWaitForDeletionCommitThenRejectReauthorization() throws Exception {
    String user = UUID.randomUUID().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    ExecutorService workers = Executors.newFixedThreadPool(3);
    java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
    try {
      jdbc.update("INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at) VALUES(?,?,'test','test',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", user, user + "@example.test");
      PrivacySettingsDto initial = privacy.get();
      followups.updatePreferences(new FollowupPreferenceUpdateRequest(true, 1, 3, "UTC"));
      var request = new PrivacySettingsRequest();
      request.setVersion(initial.version());
      request.setChatMemoryEnabled(true);
      request.setFollowupEnabled(true);
      CountDownLatch started = new CountDownLatch(3);
      new TransactionTemplate(transactions).executeWithoutResult(status -> {
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
        // Keep a terminal deletion marker so the background worker cannot consume the fixture.
        jdbc.update("INSERT INTO data_requests(id,user_id,type,status,requested_at,attempts) VALUES(?,?,'delete_account','succeeded',UTC_TIMESTAMP(3),0)", UUID.randomUUID().toString(), user);
        jdbc.update("UPDATE user_privacy_settings SET chat_memory_enabled=0,followup_enabled=0,version=version+1 WHERE user_id=?", user);
        jdbc.update("UPDATE followup_preferences SET enabled=0 WHERE user_id=?", user);
        results.add(workers.submit(() -> {
          started.countDown();
          return assertThrows(ResponseStatusException.class, () -> privacy.update(request)).getStatusCode().value();
        }));
        results.add(workers.submit(() -> {
          started.countDown();
          return assertThrows(ResponseStatusException.class, () -> followups.updatePreferences(
              new FollowupPreferenceUpdateRequest(true, 1, 3, "UTC"))).getStatusCode().value();
        }));
        results.add(workers.submit(() -> {
          started.countDown();
          return assertThrows(ResponseStatusException.class, followups::preferences).getStatusCode().value();
        }));
        try {
          org.junit.jupiter.api.Assertions.assertTrue(started.await(5, TimeUnit.SECONDS));
          for (Future<Integer> result : results) assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      });
      for (Future<Integer> result : results) assertEquals(403, result.get(10, TimeUnit.SECONDS));
      assertEquals(0, scalar("SELECT chat_memory_enabled FROM user_privacy_settings WHERE user_id=?", user));
      assertEquals(0, scalar("SELECT followup_enabled FROM user_privacy_settings WHERE user_id=?", user));
      assertEquals(0, scalar("SELECT enabled FROM followup_preferences WHERE user_id=?", user));
    } finally {
      workers.shutdownNow();
      workers.awaitTermination(10, TimeUnit.SECONDS);
      jdbc.update("DELETE FROM data_requests WHERE user_id=?", user);
      jdbc.update("DELETE FROM privacy_settings_audit WHERE user_id=?", user);
      jdbc.update("DELETE FROM user_privacy_settings WHERE user_id=?", user);
      jdbc.update("DELETE FROM followup_preferences WHERE user_id=?", user);
      jdbc.update("DELETE FROM users WHERE id=?", user);
    }
  }
}
