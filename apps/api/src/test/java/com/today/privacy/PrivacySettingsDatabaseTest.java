package com.today.privacy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "mybatis.configuration.local-cache-scope=STATEMENT")
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class PrivacySettingsDatabaseTest {
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }
  @Autowired PrivacySettingsMapper mapper;

  @Test @Transactional void compareAndSetPreventsStaleWritesAndInitializationDoesNotResetConsent() {
    String user = UUID.randomUUID().toString();
    var initial = new PrivacySettingsEntity(); initial.setUserId(user);
    initial.setChatMemoryEnabled(true); initial.setVoiceRetention("delete_on_success");
    initial.setFollowupEnabled(true); initial.setVersion(1); initial.setUpdatedAt(Instant.now());
    mapper.insert(initial);
    assertEquals(1, mapper.update(user, false, "delete_on_success", true, 2, Instant.now()));
    assertEquals(0, mapper.update(user, true, "delete_on_success", true, 2, Instant.now()));
    mapper.insert(initial);
    assertFalse(mapper.find(user).isChatMemoryEnabled());
    assertEquals(2, mapper.find(user).getVersion());
    assertEquals(0, mapper.update(UUID.randomUUID().toString(), true, "delete_on_success", true, 3, Instant.now()));
    assertFalse(mapper.find(user).isChatMemoryEnabled());
  }
}
