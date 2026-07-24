package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.today.persistence.MemoryEntity;
import com.today.memory.MemoryMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MysqlJsonVectorStoreTest {

  @Mock MemoryMapper memoryMapper;

  @Test
  void searchRanksByCosineSimilarity() {
    MemoryEntity a = entity("a", "[1,0]");
    MemoryEntity b = entity("b", "[0.7,0.7]");
    when(memoryMapper.listByUserId("u1", false)).thenReturn(List.of(a, b));

    MysqlJsonVectorStore store = new MysqlJsonVectorStore(memoryMapper);
    List<ScoredMemoryId> hits = store.search("u1", new float[] {1f, 0f}, 2);

    assertEquals(2, hits.size());
    assertEquals("a", hits.get(0).memoryId());
    assertTrue(hits.get(0).score() > hits.get(1).score());
  }

  @Test
  void searchSkipsMissingEmbeddings() {
    MemoryEntity a = entity("a", null);
    MemoryEntity b = entity("b", "[1,0]");
    when(memoryMapper.listByUserId("u1", false)).thenReturn(List.of(a, b));

    MysqlJsonVectorStore store = new MysqlJsonVectorStore(memoryMapper);
    List<ScoredMemoryId> hits = store.search("u1", new float[] {1f, 0f}, 5);

    assertEquals(1, hits.size());
    assertEquals("b", hits.get(0).memoryId());
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
