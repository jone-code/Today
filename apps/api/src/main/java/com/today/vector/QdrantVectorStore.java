package com.today.vector;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Qdrant 向量索引；失败时由 FallbackVectorStore 降级到 MySQL */
public class QdrantVectorStore implements VectorStore {

  private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

  private final QdrantClient client;

  public QdrantVectorStore(QdrantClient client) {
    this.client = client;
  }

  @Override
  public String provider() {
    return "qdrant";
  }

  @Override
  public void upsert(MemoryVectorRecord record) {
    upsertAll(List.of(record));
  }

  @Override
  public void upsertAll(List<MemoryVectorRecord> records) {
    try {
      client.upsertPoints(records);
    } catch (Exception e) {
      log.warn("qdrant upsert failed count={} reason={}", records.size(), e.toString());
      throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
    }
  }

  @Override
  public void delete(String memoryId) {
    try {
      client.deletePoints(List.of(memoryId));
    } catch (Exception e) {
      log.warn("qdrant delete failed id={} reason={}", memoryId, e.toString());
      throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
    }
  }

  @Override
  public void setArchived(String memoryId, String userId, boolean archived) {
    try {
      client.setArchived(memoryId, userId, archived);
    } catch (Exception e) {
      log.warn("qdrant setArchived failed id={} reason={}", memoryId, e.toString());
      throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
    }
  }

  @Override
  public List<ScoredMemoryId> search(String userId, float[] query, int topK) {
    return client.search(userId, query, topK);
  }
}
