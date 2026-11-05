package com.today.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class AgentProfileConcurrencyDatabaseTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private AgentProfileService profiles;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private LocalMediaStorage storage;
  @MockBean private IdentityService identity;

  @Test
  void resetWaitsForUploadAndReplacesItsCommittedAvatar() throws Exception {
    String user = UUID.randomUUID().toString();
    String previousId = UUID.randomUUID().toString();
    String previousPath = "agent/" + user + "/old.jpg";
    String newPath = "agent/" + user + "/new.jpg";
    CountDownLatch uploading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch resetting = new CountDownLatch(1);
    when(identity.getCurrentUserId()).thenReturn(user);
    when(storage.storeAgentAvatar(eq(user), any())).thenAnswer(call -> {
      uploading.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("upload lock timed out");
      return new LocalMediaStorage.StoredAvatar(newPath, 42, "image/jpeg");
    });
    var pool = Executors.newFixedThreadPool(2);
    try {
      jdbc.update("INSERT INTO agent_profiles (user_id,name,tone,avatar_media_id,version,created_at,updated_at) "
          + "VALUES (?,'Today','温和',?,1,NOW(3),NOW(3))", user, previousId);
      jdbc.update("INSERT INTO agent_media (id,user_id,relative_path,content_type,size_bytes,status,created_at) "
          + "VALUES (?,?,?,'image/jpeg',42,'active',NOW(3))", previousId, user, previousPath);
      var upload = pool.submit(() -> profiles.uploadAvatar(new MockMultipartFile("avatar", "new.jpg",
          "image/jpeg", new byte[] { 1, 2, 3 })));
      assertTrue(uploading.await(10, TimeUnit.SECONDS));
      var reset = pool.submit(() -> { resetting.countDown(); return profiles.resetAvatar(); });
      assertTrue(resetting.await(10, TimeUnit.SECONDS));
      assertFalse(reset.isDone(), "reset must wait while upload owns the profile row lock");
      release.countDown();
      AgentProfileDto uploaded = upload.get(10, TimeUnit.SECONDS);
      AgentProfileDto cleared = reset.get(10, TimeUnit.SECONDS);
      assertNotNull(uploaded.avatarMediaId());
      assertNull(cleared.avatarMediaId());
      assertEquals(uploaded.version() + 1, cleared.version());
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_media "
          + "WHERE user_id=? AND status='active'", Integer.class, user));
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM agent_media "
          + "WHERE user_id=? AND status='replaced'", Integer.class, user));
      assertNull(jdbc.queryForObject("SELECT avatar_media_id FROM agent_profiles WHERE user_id=?",
          String.class, user));
    } finally {
      release.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "avatar requests did not stop before cleanup");
      jdbc.update("DELETE FROM agent_media WHERE user_id=?", user);
      jdbc.update("DELETE FROM agent_profiles WHERE user_id=?", user);
    }
  }
}
