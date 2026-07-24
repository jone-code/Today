package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.aigateway.AiGatewayService;
import com.today.identity.IdentityService;
import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VectorReindexServiceTest {

  @Mock MemoryMapper memoryMapper;
  @Mock AiGatewayService aiGateway;
  @Mock VectorStore vectorStore;
  @Mock IdentityService identity;

  @InjectMocks VectorReindexService service;

  @Test
  void reindexCurrentUserUpsertsExistingEmbeddings() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(vectorStore.provider()).thenReturn("qdrant+fallback:mysql");
    MemoryEntity withVec = entity("m1", "[1,0]");
    MemoryEntity noVec = entity("m2", null);
    when(memoryMapper.listPageByUserId(eq("u1"), anyInt(), eq(0)))
        .thenReturn(List.of(withVec, noVec));

    VectorReindexResult result = service.reindexCurrentUser(false, false);

    assertEquals("user", result.scope());
    assertEquals(2, result.scanned());
    assertEquals(1, result.upserted());
    assertEquals(1, result.skippedNoVector());
    assertEquals(0, result.embedded());
    verify(vectorStore).upsertAll(anyList());
    verify(vectorStore, never()).recreateIndex();
    verify(aiGateway, never()).embed(anyList());
  }

  @Test
  void reindexAllRecreateAndFillMissing() {
    when(vectorStore.provider()).thenReturn("qdrant");
    when(vectorStore.recreateIndex()).thenReturn(true);
    MemoryEntity missing = entity("m3", null);
    when(memoryMapper.listPage(anyInt(), eq(0))).thenReturn(List.of(missing));
    when(aiGateway.embed(List.of("m3")))
        .thenReturn(Optional.of(List.of(new float[] {0.1f, 0.2f})));

    VectorReindexResult result = service.reindexAll(true, true);

    assertEquals("all", result.scope());
    assertTrue(result.recreate());
    assertEquals(1, result.embedded());
    assertEquals(1, result.upserted());
    assertEquals(0, result.skippedNoVector());
    verify(vectorStore).recreateIndex();
    verify(memoryMapper).update(missing);
  }

  private static MemoryEntity entity(String id, String embeddingJson) {
    MemoryEntity e = new MemoryEntity();
    e.setId(id);
    e.setUserId("u1");
    e.setCategory("work");
    e.setMemoryText(id);
    e.setStrength(1);
    e.setArchived(false);
    e.setEmbeddingJson(embeddingJson);
    return e;
  }
}
