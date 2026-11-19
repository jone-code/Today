package com.today.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.today.common.EntityMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TODAY_TEST_MYSQL_URL", matches = ".+")
class ProductEventDatabaseTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private ProductEventMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void duplicateClientRequestIsStoredOnce() {
    String userId = UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    try {
      ProductEventEntity first = event(userId, requestId);
      ProductEventEntity second = event(userId, requestId);
      mapper.insert(first);
      mapper.insert(second);
      assertEquals(1, jdbc.queryForObject(
          "SELECT COUNT(*) FROM product_events WHERE user_id=? AND client_request_id=?",
          Integer.class, userId, requestId));
    } finally {
      jdbc.update("DELETE FROM product_events WHERE user_id=?", userId);
    }
  }

  @Test
  void startedAndSavedEventsUseDifferentRequestIdsButEachRetryIsIdempotent() {
    String userId = UUID.randomUUID().toString();
    String baseRequestId = UUID.randomUUID().toString();
    try {
      mapper.insert(event(userId, baseRequestId + ":started", "checkin_started"));
      mapper.insert(event(userId, baseRequestId + ":started", "checkin_started"));
      mapper.insert(event(userId, baseRequestId + ":saved", "checkin_saved"));
      mapper.insert(event(userId, baseRequestId + ":saved", "checkin_saved"));
      assertEquals(2, jdbc.queryForObject(
          "SELECT COUNT(*) FROM product_events WHERE user_id=? AND client_request_id LIKE ?",
          Integer.class, userId, baseRequestId + ":%"));
    } finally {
      jdbc.update("DELETE FROM product_events WHERE user_id=?", userId);
    }
  }

  private static ProductEventEntity event(String userId, String requestId) {
    return event(userId, requestId, "checkin_saved");
  }

  private static ProductEventEntity event(String userId, String requestId, String eventName) {
    ProductEventEntity event = new ProductEventEntity();
    event.setId(UUID.randomUUID().toString());
    event.setUserId(userId);
    event.setEventName(eventName);
    event.setPlatform("android");
    event.setEventSchemaVersion(1);
    event.setClientRequestId(requestId);
    event.setOccurredAt(EntityMapper.now());
    event.setReceivedAt(EntityMapper.now());
    return event;
  }
}
