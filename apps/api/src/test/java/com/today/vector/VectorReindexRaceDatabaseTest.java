package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.aigateway.AiGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.identity.IdentityService;
import com.today.memory.MemoryMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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
class VectorReindexRaceDatabaseTest {
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("TODAY_TEST_MYSQL_URL"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MemoryMapper memories;
  @Autowired private VectorSyncJobMapper jobs;

  @Test
  void deletingSourceWhileReindexUploadsLeavesAReconciliationJob() {
    String user = UUID.randomUUID().toString();
    String memory = UUID.randomUUID().toString();
    String checkin = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO checkins (id,user_id,checkin_date,raw_text,created_at,updated_at) "
        + "VALUES (?,?,CURRENT_DATE(),'source',NOW(3),NOW(3))", checkin, user);
    jdbc.update("INSERT INTO memories (id,user_id,category,memory_text,strength,archived,embedding_json,updated_at) "
        + "VALUES (?,?,'life','sensitive',1,0,'[1,0]',NOW(3))", memory, user);
    jdbc.update("INSERT INTO memory_sources (memory_id,user_id,source_type,source_id,active,created_at) "
        + "VALUES (?,?,'checkin',?,1,NOW(3))", memory, user, checkin);
    try {
      IdentityService identity = mock(IdentityService.class);
      when(identity.getCurrentUserId()).thenReturn(user);
      VectorStore store = mock(VectorStore.class);
      when(store.provider()).thenReturn("qdrant");
      doAnswer(invocation -> {
        jdbc.update("UPDATE memory_sources SET active=0 WHERE memory_id=?", memory);
        jdbc.update("DELETE FROM checkins WHERE id=?", checkin);
        return null;
      }).when(store).upsertAll(anyList());

      var reindex = new VectorReindexService(memories, mock(AiGatewayService.class), store, identity, jobs);
      assertEquals(1, reindex.reindexCurrentUser(false, false).upserted());
      assertEquals(1, jobs.ready(Instant.now().plusSeconds(5), 50).size());

      VectorProperties properties = new VectorProperties();
      properties.setProvider("qdrant");
      QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
      assertEquals(1, new VectorSyncScheduler(jobs, memories, properties, qdrant).processDue(50));
      verify(qdrant).delete(memory);
      assertEquals(0, jobs.ready(Instant.now().plusSeconds(5), 50).size());
    } finally {
      jdbc.update("DELETE FROM vector_sync_jobs WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM memory_sources WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM memories WHERE id=?", memory);
      jdbc.update("DELETE FROM checkins WHERE id=?", checkin);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "TODAY_TEST_MOCK_QDRANT_URL", matches = ".+")
  void realHttpUpsertIsRemovedAfterTheSourceIsRevoked() throws Exception {
    String user = UUID.randomUUID().toString();
    String memory = UUID.randomUUID().toString();
    String checkin = UUID.randomUUID().toString();
    String mockUrl = System.getenv("TODAY_TEST_MOCK_QDRANT_URL");
    String pointId = QdrantClient.pointId(memory).toString();
    VectorProperties properties = new VectorProperties();
    properties.setProvider("qdrant");
    properties.setQdrantUrl(mockUrl);
    QdrantVectorStore qdrant = new QdrantVectorStore(new QdrantClient(properties, new ObjectMapper()), properties);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn(user);
    jdbc.update("INSERT INTO checkins (id,user_id,checkin_date,raw_text,created_at,updated_at) "
        + "VALUES (?,?,CURRENT_DATE(),'source',NOW(3),NOW(3))", checkin, user);
    jdbc.update("INSERT INTO memories (id,user_id,category,memory_text,strength,archived,embedding_json,updated_at) "
        + "VALUES (?,?,'life','sensitive',1,0,'[1,0]',NOW(3))", memory, user);
    jdbc.update("INSERT INTO memory_sources (memory_id,user_id,source_type,source_id,active,created_at) "
        + "VALUES (?,?,'checkin',?,1,NOW(3))", memory, user, checkin);
    try {
      var reindex = new VectorReindexService(memories, mock(AiGatewayService.class), qdrant, identity, jobs);
      assertEquals(1, reindex.reindexCurrentUser(false, false).upserted());
      assertTrue(mockHasPoint(mockUrl, pointId));

      jdbc.update("UPDATE memory_sources SET active=0 WHERE memory_id=?", memory);
      assertEquals(1, new VectorSyncScheduler(jobs, memories, properties, qdrant).processDue(50));
      assertFalse(mockHasPoint(mockUrl, pointId));
    } finally {
      jdbc.update("DELETE FROM vector_sync_jobs WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM memory_sources WHERE memory_id=?", memory);
      jdbc.update("DELETE FROM memories WHERE id=?", memory);
      jdbc.update("DELETE FROM checkins WHERE id=?", checkin);
    }
  }

  private boolean mockHasPoint(String mockUrl, String pointId) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(mockUrl + "/__stats")).GET().build();
    String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    for (JsonNode id : new ObjectMapper().readTree(body).path("pointIds")) {
      if (pointId.equals(id.asText())) return true;
    }
    return false;
  }
}
