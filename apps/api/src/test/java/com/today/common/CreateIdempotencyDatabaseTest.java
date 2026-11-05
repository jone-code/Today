package com.today.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.identity.IdentityService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class CreateIdempotencyDatabaseTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PlatformTransactionManager transactions;

  @Test
  void replayConflictRollbackAndConcurrentCreation() throws Exception {
    String userId = UUID.randomUUID().toString();
    String key = UUID.randomUUID().toString();
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn(userId);
    CreateIdempotencyService service = new CreateIdempotencyService(jdbc, json, identity);
    TransactionTemplate tx = new TransactionTemplate(transactions);
    AtomicInteger created = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> {
        start.await();
        return tx.execute(status -> service.create("todo", key, "one", String.class,
            () -> { created.incrementAndGet(); return "created"; }));
      });
      var second = pool.submit(() -> {
        start.await();
        return tx.execute(status -> service.create("todo", key, "one", String.class,
            () -> { created.incrementAndGet(); return "duplicate"; }));
      });
      start.countDown();
      String result = first.get(10, TimeUnit.SECONDS);
      assertEquals(result, second.get(10, TimeUnit.SECONDS));
      assertEquals(1, created.get());
      assertEquals(result, tx.execute(status -> service.create("todo", key, "one", String.class,
          () -> "never")));
      ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
          () -> tx.execute(status -> service.create("todo", key, "different", String.class,
              () -> "never")));
      assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM create_requests WHERE user_id=?", Integer.class, userId));

      String lower = UUID.randomUUID().toString();
      String upper = lower.toUpperCase(java.util.Locale.ROOT);
      assertEquals("lower", tx.execute(status -> service.create("reminder", lower, "same", String.class,
          () -> "lower")));
      assertEquals("upper", tx.execute(status -> service.create("reminder", upper, "same", String.class,
          () -> "upper")));

      String failedKey = UUID.randomUUID().toString();
      assertThrows(IllegalStateException.class, () -> tx.execute(status ->
          service.create("habit", failedKey, "one", String.class,
              () -> { throw new IllegalStateException("create failed"); })));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM create_requests WHERE user_id=? AND request_id=?",
          Integer.class, userId, failedKey));
      assertEquals("recovered", tx.execute(status -> service.create("habit", failedKey, "one", String.class,
          () -> "recovered")));
    } finally {
      pool.shutdownNow();
      jdbc.update("DELETE FROM create_requests WHERE user_id=?", userId);
    }
  }
}
