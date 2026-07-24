package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FallbackVectorStoreTest {

  @Mock VectorStore primary;
  @Mock VectorStore fallback;

  @Test
  void searchFallsBackWhenPrimaryEmpty() {
    when(primary.search(eq("u1"), any(), anyInt())).thenReturn(List.of());
    when(fallback.search(eq("u1"), any(), anyInt()))
        .thenReturn(List.of(new ScoredMemoryId("m1", 0.9)));

    FallbackVectorStore store = new FallbackVectorStore(primary, fallback);
    List<ScoredMemoryId> hits = store.search("u1", new float[] {1f}, 3);

    assertEquals(1, hits.size());
    assertEquals("m1", hits.get(0).memoryId());
  }

  @Test
  void searchFallsBackWhenPrimaryThrows() {
    when(primary.search(eq("u1"), any(), anyInt())).thenThrow(new RuntimeException("down"));
    when(fallback.search(eq("u1"), any(), anyInt()))
        .thenReturn(List.of(new ScoredMemoryId("m2", 0.5)));

    FallbackVectorStore store = new FallbackVectorStore(primary, fallback);
    assertEquals("m2", store.search("u1", new float[] {1f}, 3).get(0).memoryId());
  }

  @Test
  void upsertFailureIsSwallowed() {
    doThrow(new RuntimeException("down")).when(primary).upsert(any());
    FallbackVectorStore store = new FallbackVectorStore(primary, fallback);
    store.upsert(
        new MemoryVectorRecord("id", "u1", "work", "t", 1, false, new float[] {1f}));
    verify(primary).upsert(any());
  }
}
