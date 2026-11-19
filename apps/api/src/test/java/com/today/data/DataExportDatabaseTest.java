package com.today.data;

import com.today.identity.AuthUserPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class DataExportDatabaseTest {
  @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
    registry.add("today.data.export-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }
  @Autowired DataRequestService service;
  @Autowired DataRequestController controller;
  @Autowired JdbcTemplate db;
  @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
  private void login(String user) {
    var principal = new AuthUserPrincipal(user, user + "@example.test", "", "test");
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }
  private String account() {
    String user = UUID.randomUUID().toString();
    db.update("INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at) VALUES(?,?,'export test','test',NOW(3),NOW(3))", user, user + "@example.test");
    login(user); return user;
  }
  @Test @Transactional void durableQueueIdempotencyAndSingleUseDownload() throws Exception {
    String user = account(); String key = UUID.randomUUID().toString();
    db.update("INSERT INTO checkins(id,user_id,checkin_date,raw_text,created_at,updated_at) VALUES(?,?,CURRENT_DATE(),'exported record',NOW(3),NOW(3))", UUID.randomUUID().toString(), user);
    db.update("INSERT INTO privacy_settings_audit(id,user_id,field,old_value,new_value,source,occurred_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3))",
        UUID.randomUUID().toString(), user, "chat_memory_enabled", "true", "false", "profile_privacy");
    var task = controller.export(key);
    assertEquals("queued", task.status());
    assertEquals(task.id(), controller.export(key).id());
    assertEquals(task.id(), controller.export(UUID.randomUUID().toString()).id());
    assertTrue(service.processNextExport());
    assertEquals("succeeded", service.get(task.id()).status());
    Instant.parse(service.get(task.id()).requestedAt());
    String token = service.issueDownloadToken(task.id());
    login(UUID.randomUUID().toString());
    assertThrows(ResponseStatusException.class, () -> service.download(task.id(), token));
    login(user);
    Path output = service.download(task.id(), token);
    assertFalse(new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.ISO_8859_1).contains("exported record"));
    Path decoded = Files.createTempFile("today-export-test-", ".zip");
    try (var input = service.openDownload(output)) { Files.copy(input, decoded, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    try (ZipFile zip = new ZipFile(decoded.toFile())) {
      assertNotNull(zip.getEntry("manifest.json"));
      var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
      for (var entry : manifest.get("tables")) {
        byte[] contents = zip.getInputStream(zip.getEntry(entry.get("file").asText())).readAllBytes();
        assertEquals(entry.get("sha256").asText(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contents)));
      }
      assertNotNull(zip.getEntry("checkins.csv"));
      assertNotNull(zip.getEntry("privacy_settings_audit.json"));
      assertNull(zip.getEntry("metrics_access_audit.json"));
      assertNotNull(zip.getEntry("media.json"));
      String data = new String(zip.getInputStream(zip.getEntry("checkins.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(data.contains("exported record"));
    }
    Files.delete(decoded);
    assertThrows(ResponseStatusException.class, () -> service.download(task.id(), token));
    String shortLived = service.issueDownloadToken(task.id());
    db.update("UPDATE data_requests SET download_token_expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", task.id());
    assertThrows(ResponseStatusException.class, () -> service.download(task.id(), shortLived));
    String refreshed = service.issueDownloadToken(task.id());
    db.update("UPDATE data_requests SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", task.id());
    assertThrows(ResponseStatusException.class, () -> service.download(task.id(), refreshed));
    service.cleanupExpiredExports();
    assertFalse(Files.exists(output));
    assertEquals("expired", service.get(task.id()).status());
    assertEquals(task.id(), service.retry(task.id()).id());
    assertEquals("queued", service.get(task.id()).status());
  }
  @Test @Transactional void deletionIsDurableIdempotentAndRetryResumesStep() throws Exception {
    String user = account();
    String other = account();
    login(user);
    db.update("INSERT INTO privacy_settings_audit(id,user_id,field,old_value,new_value,source,occurred_at) "
        + "VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3))", java.util.UUID.randomUUID().toString(), user,
        "chat_memory_enabled", "true", "false", "profile_privacy");
    String metricsAudit = java.util.UUID.randomUUID().toString();
    db.update("INSERT INTO metrics_access_audit(id,endpoint,from_date,to_date,accessed_at) VALUES (?,?,CURRENT_DATE,CURRENT_DATE,UTC_TIMESTAMP(3))",
        metricsAudit, "test");
    assertThrows(ResponseStatusException.class, () -> service.deleteAccount("wrong"));
    var task = service.deleteAccount("DELETE MY ACCOUNT");
    assertEquals("queued", task.status());
    assertEquals(0, db.queryForObject("SELECT chat_memory_enabled FROM user_privacy_settings WHERE user_id=?", Integer.class, user));
    assertEquals(0, db.queryForObject("SELECT followup_enabled FROM user_privacy_settings WHERE user_id=?", Integer.class, user));
    assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM privacy_settings_audit WHERE user_id=? AND source='account_deletion'", Integer.class, user));
    assertEquals(task.id(), service.deleteAccount("DELETE MY ACCOUNT").id());
    assertThrows(ResponseStatusException.class, service::createExport);
    login(other);
    assertThrows(ResponseStatusException.class, () -> service.get(task.id()));
    login(user);
    assertTrue(service.processNextDeletion());
    assertEquals(1, db.queryForObject("SELECT step FROM data_requests WHERE id=?", Integer.class, task.id()));
    db.update("UPDATE data_requests SET status='failed',attempts=1 WHERE id=?", task.id());
    assertEquals(task.id(), service.retry(task.id()).id());
    assertTrue(service.processNextDeletion());
    assertTrue(service.processNextDeletion());
    assertEquals("succeeded", service.get(task.id()).status());
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, user));
    assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM privacy_settings_audit WHERE user_id=?", Integer.class, user));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM metrics_access_audit WHERE id=?", Integer.class, metricsAudit));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, other));
  }

  @Test @Transactional void deletionFailurePreservesStepAndCanResumeWithoutLosingOtherAccounts() {
    String user = account();
    var identity = org.mockito.Mockito.mock(com.today.identity.IdentityService.class);
    org.mockito.Mockito.when(identity.getCurrentUserId()).thenReturn(user);
    var media = org.mockito.Mockito.mock(com.today.media.LocalMediaStorage.class);
    org.mockito.Mockito.doThrow(new IllegalStateException("disk unavailable")).doNothing().when(media).deleteUserMedia(user);
    var vectors = org.mockito.Mockito.mock(com.today.vector.VectorStore.class);
    var worker = new DataRequestService(db.getDataSource(), identity, media, vectors, new com.fasterxml.jackson.databind.ObjectMapper(), new ExportEncryption("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
    var task = service.deleteAccount("DELETE MY ACCOUNT");
    assertTrue(worker.processNextDeletion());
    assertTrue(worker.processNextDeletion());
    assertEquals("failed", service.get(task.id()).status());
    assertEquals(1, db.queryForObject("SELECT step FROM data_requests WHERE id=?", Integer.class, task.id()));
    assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, user));
    service.retry(task.id());
    assertTrue(worker.processNextDeletion());
    assertTrue(worker.processNextDeletion());
    assertEquals("succeeded", service.get(task.id()).status());
    org.mockito.Mockito.verify(media, org.mockito.Mockito.times(3)).deleteUserMedia(user);
  }

}
