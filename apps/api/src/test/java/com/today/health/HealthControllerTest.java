package com.today.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.today.aigateway.AiCallObserver;
import com.today.aigateway.AiGatewayService;
import com.today.media.LocalMediaStorage;
import com.today.vector.VectorHealth;
import com.today.vector.VectorReindexService;
import com.today.vector.VectorStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
  @Test
  void readinessReturnsUnavailableWhenDatabaseFails() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForObject("SELECT 1", Integer.class)).thenThrow(new IllegalStateException("offline"));
    VectorReindexService vector = mock(VectorReindexService.class);
    when(vector.health()).thenReturn(VectorHealth.mysqlOk(0));
    HealthController controller = controller(db, vector);

    var response = controller.ready();

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals(false, response.getBody().get("database"));
    assertEquals(true, response.getBody().get("vector"));
    assertEquals(false, response.getBody().get("ok"));
  }

  @Test
  void readinessReturnsOkWhenDatabaseAndVectorAreHealthy() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    VectorReindexService vector = mock(VectorReindexService.class);
    when(vector.health()).thenReturn(VectorHealth.mysqlOk(3));
    HealthController controller = controller(db, vector);

    var response = controller.ready();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue((Boolean) response.getBody().get("ok"));
  }

  @Test
  void readinessReturnsUnavailableWhenVectorIsUnhealthy() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    VectorReindexService vector = mock(VectorReindexService.class);
    when(vector.health()).thenReturn(VectorHealth.unavailable("qdrant", "connection refused"));
    HealthController controller = controller(db, vector);

    var response = controller.ready();

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals(true, response.getBody().get("database"));
    assertEquals(false, response.getBody().get("vector"));
    assertEquals(false, response.getBody().get("ok"));
  }

  private static HealthController controller(JdbcTemplate db, VectorReindexService vector) {
    AiGatewayService ai = mock(AiGatewayService.class);
    AiCallObserver observer = mock(AiCallObserver.class);
    VectorStore store = mock(VectorStore.class);
    LocalMediaStorage media = mock(LocalMediaStorage.class);
    when(store.provider()).thenReturn("mysql");
    when(media.getRoot()).thenReturn(Path.of("/tmp/today-media"));
    return new HealthController(ai, observer, store, vector, media, db);
  }
}
