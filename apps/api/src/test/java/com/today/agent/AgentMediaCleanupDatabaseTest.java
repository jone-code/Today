package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class AgentMediaCleanupDatabaseTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private AgentMediaMapper media;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void deferredFirstPageDoesNotStarveLaterReplacedFiles() {
    String user = UUID.randomUUID().toString();
    try {
      for (int i = 0; i < 101; i++) {
        jdbc.update("INSERT INTO agent_media (id,user_id,relative_path,content_type,size_bytes,status,created_at,replaced_at) "
            + "VALUES (?,?,?,'image/jpeg',1,'replaced',NOW(3),DATE_SUB(NOW(3), INTERVAL ? MINUTE))",
            UUID.randomUUID().toString(), user, "agent/" + user + "/" + i + ".jpg", 102 - i);
      }

      var firstPage = media.listReplaced(100).stream().filter(item -> user.equals(item.getUserId())).toList();
      assertEquals(100, firstPage.size());
      for (var item : firstPage) {
        assertEquals(1, media.deferCleanup(item.getId(), user));
      }

      var nextPage = media.listReplaced(100).stream().filter(item -> user.equals(item.getUserId())).toList();
      assertEquals(1, nextPage.size());
      assertTrue(nextPage.get(0).getRelativePath().endsWith("/100.jpg"));
      assertEquals(1, media.markPurged(nextPage.get(0).getId(), user));
      assertEquals(0, media.listReplaced(100).stream().filter(item -> user.equals(item.getUserId())).count());
      assertEquals(100, jdbc.queryForObject("SELECT COUNT(*) FROM agent_media "
          + "WHERE user_id=? AND status='replaced' AND next_cleanup_at > NOW(3)", Integer.class, user));
    } finally {
      jdbc.update("DELETE FROM agent_media WHERE user_id=?", user);
    }
  }
}
