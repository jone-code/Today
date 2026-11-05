package com.today.punch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.today.media.LocalMediaStorage;
import com.today.media.MediaController;
import com.today.identity.IdentityService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class PunchPhotoCleanupDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private PunchPhotoCleanupMapper jobs;
  @Autowired private PunchMapper punches;
  @Autowired private PunchPhotoCleanupScheduler scheduler;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private PunchService punchService;
  @Autowired private PunchController punchController;
  @Autowired private MediaController mediaController;
  @MockBean private LocalMediaStorage storage;
  @MockBean private IdentityService identity;

  @Test
  void photoRetryReplaysFirstResultAndRejectsChangedImage() {
    String habitId = UUID.randomUUID().toString();
    String user = UUID.randomUUID().toString();
    String key = UUID.randomUUID().toString();
    String path = "punch/" + user + "/first.jpg";
    String day = LocalDate.now().toString();
    MockMultipartFile image = new MockMultipartFile("photo", "punch.jpg", MediaType.IMAGE_JPEG_VALUE,
        new byte[] {1, 2, 3});
    when(identity.getCurrentUserId()).thenReturn(user);
    when(storage.storePunchPhoto(eq(user), any())).thenReturn(path);
    when(storage.publicUrl(path)).thenReturn("/v1/media/" + path);
    try {
      jdbc.update("INSERT INTO punch_habits (id,user_id,title,enabled,created_at,updated_at) "
          + "VALUES (?,?,'test',1,NOW(3),NOW(3))", habitId, user);
      var first = punchController.punchWithPhoto(habitId, day, null, image, key);
      var repeated = punchController.punchWithPhoto(habitId, day, null, image, key);
      assertEquals(first, repeated);
      verify(storage, times(1)).storePunchPhoto(eq(user), any());
      var conflict = assertThrows(ResponseStatusException.class, () -> punchController.punchWithPhoto(
          habitId, day, null, new MockMultipartFile("photo", "punch.jpg", MediaType.IMAGE_JPEG_VALUE,
              new byte[] {4, 5, 6}), key));
      assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM punch_logs WHERE habit_id=?", Integer.class, habitId));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE user_id=?",
          Integer.class, user));
    } finally {
      jdbc.update("DELETE FROM create_requests WHERE user_id=?", user);
      jdbc.update("DELETE FROM punch_logs WHERE habit_id=?", habitId);
      jdbc.update("DELETE FROM punch_habits WHERE id=?", habitId);
      jdbc.update("DELETE FROM punch_photo_cleanup_jobs WHERE user_id=?", user);
    }
  }

  @Test
  void rolledBackPhotoReplacementDeletesOnlyTheNewFile() {
    String habitId = UUID.randomUUID().toString();
    String user = UUID.randomUUID().toString();
    String oldPath = "punch/" + user + "/old.jpg";
    String newPath = "punch/" + user + "/new.jpg";
    String day = LocalDate.now().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    when(storage.storePunchPhoto(eq(user), any())).thenReturn(newPath);
    when(storage.deleteIfPresent(newPath)).thenReturn(true);
    try {
      jdbc.update("INSERT INTO punch_habits (id,user_id,title,enabled,created_at,updated_at) "
          + "VALUES (?,?,'test',1,NOW(3),NOW(3))", habitId, user);
      jdbc.update("INSERT INTO punch_logs (id,habit_id,user_id,punch_date,photo_path,created_at) "
          + "VALUES (?,?,?, ?, ?, NOW(3))", UUID.randomUUID().toString(), habitId, user, day, oldPath);
      TransactionTemplate tx = new TransactionTemplate(transactions);
      assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
        punchService.punchWithPhoto(habitId, day, null, new MockMultipartFile(
            "photo", "punch.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1}));
        throw new IllegalStateException("rollback");
      }));
      verify(storage).deleteIfPresent(newPath);
      verify(storage, never()).deleteIfPresent(oldPath);
      assertEquals(oldPath, jdbc.queryForObject("SELECT photo_path FROM punch_logs WHERE habit_id=?",
          String.class, habitId));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE user_id=?",
          Integer.class, user));
    } finally {
      jdbc.update("DELETE FROM punch_logs WHERE habit_id=?", habitId);
      jdbc.update("DELETE FROM punch_habits WHERE id=?", habitId);
      jdbc.update("DELETE FROM punch_photo_cleanup_jobs WHERE user_id=?", user);
    }
  }

  @Test
  void failedRollbackDeletionIsQueuedInANewTransaction() {
    String habitId = UUID.randomUUID().toString();
    String user = UUID.randomUUID().toString();
    String path = "punch/" + user + "/orphan.jpg";
    String day = LocalDate.now().toString();
    when(identity.getCurrentUserId()).thenReturn(user);
    when(storage.storePunchPhoto(eq(user), any())).thenReturn(path);
    when(storage.deleteIfPresent(path)).thenReturn(false, true);
    try {
      jdbc.update("INSERT INTO punch_habits (id,user_id,title,enabled,created_at,updated_at) "
          + "VALUES (?,?,'test',1,NOW(3),NOW(3))", habitId, user);
      TransactionTemplate tx = new TransactionTemplate(transactions);
      assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
        punchService.punchWithPhoto(habitId, day, null, new MockMultipartFile(
            "photo", "punch.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1}));
        throw new IllegalStateException("rollback");
      }));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_logs WHERE habit_id=?",
          Integer.class, habitId));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE path=?",
          Integer.class, path));
      assertEquals(1, scheduler.processDue(100));
      verify(storage, times(2)).deleteIfPresent(path);
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE path=?",
          Integer.class, path));
    } finally {
      jdbc.update("DELETE FROM punch_logs WHERE habit_id=?", habitId);
      jdbc.update("DELETE FROM punch_habits WHERE id=?", habitId);
      jdbc.update("DELETE FROM punch_photo_cleanup_jobs WHERE path=?", path);
    }
  }

  @Test
  void deletingHabitRevokesPhotoAccessAndPersistsCleanupJob() {
    String id = UUID.randomUUID().toString();
    String logId = UUID.randomUUID().toString();
    String user = "photo-test-user";
    String path = "punch/" + user + "/" + logId + ".jpg";
    when(identity.getCurrentUserId()).thenReturn(user);
    try {
      jdbc.update("INSERT INTO punch_habits (id, user_id, title, enabled, created_at, updated_at) "
          + "VALUES (?, ?, 'test', 1, NOW(3), NOW(3))", id, user);
      jdbc.update("INSERT INTO punch_logs (id, habit_id, user_id, punch_date, photo_path, created_at) "
          + "VALUES (?, ?, ?, CURDATE(), ?, NOW(3))", logId, id, user, path);
      punchService.deleteHabit(id);
      assertFalse(punches.isPhotoReferenced(path, user));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE path = ?",
          Integer.class, path));
      assertEquals(404, mediaController.getMedia("/" + path).getStatusCode().value());
      verify(storage, never()).deleteIfPresent(path);
    } finally {
      jdbc.update("DELETE FROM punch_logs WHERE id = ?", logId);
      jdbc.update("DELETE FROM punch_habits WHERE id = ?", id);
      jdbc.update("DELETE FROM punch_photo_cleanup_jobs WHERE path = ?", path);
    }
  }

  @Test
  void rollbackAndRetryPreservePhotoUntilReferenceIsRemoved() {
    String id = UUID.randomUUID().toString();
    String user = "photo-test-user";
    String path = "punch/" + user + "/" + id + ".jpg";
    try {
      TransactionTemplate tx = new TransactionTemplate(transactions);
      assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
        jobs.enqueue(path, user, Instant.now());
        throw new IllegalStateException("rollback");
      }));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE path = ?",
          Integer.class, path));

      jdbc.update("INSERT INTO punch_logs (id, habit_id, user_id, punch_date, photo_path, created_at) "
          + "VALUES (?, ?, ?, CURDATE(), ?, NOW(3))", id, id, user, path);
      assertTrue(punches.isPhotoReferenced(path, user));
      jobs.enqueue(path, user, Instant.now());
      assertEquals(user, jobs.ready(Instant.now().plusSeconds(1), 100).stream()
          .filter(job -> path.equals(job.getPath())).findFirst().orElseThrow().getUserId());
      scheduler.processDue(100);
      verify(storage, never()).deleteIfPresent(path);
      assertEquals(1, jdbc.queryForObject("SELECT attempts FROM punch_photo_cleanup_jobs WHERE path = ?",
          Integer.class, path));

      jdbc.update("DELETE FROM punch_logs WHERE id = ?", id);
      jdbc.update("UPDATE punch_photo_cleanup_jobs SET next_attempt_at = NOW(3) WHERE path = ?", path);
      when(storage.deleteIfPresent(path)).thenReturn(false, true);
      scheduler.processDue(100);
      assertEquals(2, jdbc.queryForObject("SELECT attempts FROM punch_photo_cleanup_jobs WHERE path = ?",
          Integer.class, path));
      jdbc.update("UPDATE punch_photo_cleanup_jobs SET next_attempt_at = NOW(3) WHERE path = ?", path);
      assertEquals(1, scheduler.processDue(100));
      verify(storage, times(2)).deleteIfPresent(path);
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM punch_photo_cleanup_jobs WHERE path = ?",
          Integer.class, path));
    } finally {
      jdbc.update("DELETE FROM punch_logs WHERE id = ?", id);
      jdbc.update("DELETE FROM punch_photo_cleanup_jobs WHERE path = ?", path);
    }
  }
}
