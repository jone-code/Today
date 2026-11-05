package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.today.aigateway.AiGatewayService;
import com.today.identity.IdentityService;
import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
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
  @Mock VectorSyncJobMapper vectorJobs;

  @InjectMocks VectorReindexService service;

  @Test
  void reindexCurrentUserUpsertsExistingEmbeddings() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(vectorStore.provider()).thenReturn("qdrant+fallback:mysql");
    MemoryEntity withVec = entity("m1", "[1,0]");
    MemoryEntity noVec = entity("m2", null);
    when(memoryMapper.listPageByUserId(eq("u1"), anyInt(), eq(0)))
        .thenReturn(List.of(withVec, noVec));
    when(memoryMapper.findConfirmedById("m1", "u1")).thenReturn(withVec);
    when(memoryMapper.findConfirmedById("m2", "u1")).thenReturn(noVec);

    VectorReindexResult result = service.reindexCurrentUser(false, false);

    assertEquals("user", result.scope());
    assertEquals(2, result.scanned());
    assertEquals(1, result.upserted());
    assertEquals(1, result.skippedNoVector());
    assertEquals(0, result.embedded());
    verify(vectorStore).upsertAll(anyList());
    verify(vectorJobs).enqueue(eq("m1"), eq("u1"), any());
    verify(vectorStore, never()).recreateIndex();
    verify(aiGateway, never()).embed(anyList());
  }

  @Test
  void reindexAllRecreateAndFillMissing() {
    when(vectorStore.provider()).thenReturn("qdrant");
    when(vectorStore.recreateIndex()).thenReturn(true);
    MemoryEntity missing = entity("m3", null);
    when(memoryMapper.listPage(anyInt(), eq(0))).thenReturn(List.of(missing));
    when(memoryMapper.findConfirmedById("m3", "u1")).thenReturn(missing);
    when(aiGateway.embed(List.of("m3")))
        .thenReturn(Optional.of(List.of(new float[] {0.1f, 0.2f})));
    when(memoryMapper.updateEmbeddingIfConfirmed(eq("m3"), eq("u1"), eq("m3"),
        any(Instant.class), any(String.class), any(Instant.class))).thenAnswer(invocation -> {
          missing.setEmbeddingJson(invocation.getArgument(4));
          return 1;
        });

    VectorReindexResult result = service.reindexAll(true, true);

    assertEquals("all", result.scope());
    assertTrue(result.recreate());
    assertEquals(1, result.embedded());
    assertEquals(1, result.upserted());
    assertEquals(0, result.skippedNoVector());
    verify(vectorStore).recreateIndex();
    verify(memoryMapper).updateEmbeddingIfConfirmed(eq("m3"), eq("u1"), eq("m3"),
        any(Instant.class), any(String.class), any(Instant.class));
  }

  @Test
  void reindexNeverEmbedsOrUpsertsArchivedMemories() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(vectorStore.provider()).thenReturn("mysql");
    MemoryEntity archived = entity("private", "[1,0]");
    archived.setArchived(true);
    when(memoryMapper.listPageByUserId(eq("u1"), anyInt(), eq(0)))
        .thenReturn(List.of(archived));

    VectorReindexResult result = service.reindexCurrentUser(true, false);

    assertEquals(1, result.skippedNoVector());
    assertEquals(0, result.upserted());
    verify(aiGateway, never()).embed(anyList());
    verify(vectorStore, never()).upsertAll(anyList());
    verify(vectorJobs).enqueue(eq("private"), eq("u1"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void reindexDoesNotSendInvalidatedMemoryTextToEmbeddingOrVectorStore() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(vectorStore.provider()).thenReturn("qdrant");
    MemoryEntity invalid = entity("invalid", null);
    when(memoryMapper.listPageByUserId(eq("u1"), anyInt(), eq(0)))
        .thenReturn(List.of(invalid));

    VectorReindexResult result = service.reindexCurrentUser(true, false);

    assertEquals(1, result.skippedNoVector());
    assertEquals(0, result.embedded());
    verify(aiGateway, never()).embed(anyList());
    verify(vectorStore, never()).upsertAll(anyList());
    verify(vectorJobs).enqueue(eq("invalid"), eq("u1"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletionDuringEmbeddingCannotPersistOrIndexStaleMemory() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(vectorStore.provider()).thenReturn("qdrant");
    MemoryEntity memory = entity("m4", null);
    when(memoryMapper.listPageByUserId(eq("u1"), anyInt(), eq(0)))
        .thenReturn(List.of(memory));
    when(memoryMapper.findConfirmedById("m4", "u1")).thenReturn(memory, null);
    when(aiGateway.embed(List.of("m4"))).thenReturn(Optional.of(List.of(new float[] {1f, 0f})));

    VectorReindexResult result = service.reindexCurrentUser(true, false);

    assertEquals(0, result.embedded());
    assertEquals(0, result.upserted());
    verify(memoryMapper).updateEmbeddingIfConfirmed(eq("m4"), eq("u1"), eq("m4"),
        any(Instant.class), any(String.class), any(Instant.class));
    verify(vectorStore, never()).upsertAll(anyList());
    verify(vectorJobs).enqueue(eq("m4"), eq("u1"), any());
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
    e.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return e;
  }
}
