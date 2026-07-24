package com.today.vector;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主存储优先；写入失败吞掉并记日志，检索失败回退到 fallback（通常为 MySQL JSON）。
 */
public class FallbackVectorStore implements VectorStore {

  private static final Logger log = LoggerFactory.getLogger(FallbackVectorStore.class);

  private final VectorStore primary;
  private final VectorStore fallback;

  public FallbackVectorStore(VectorStore primary, VectorStore fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public String provider() {
    return primary.provider() + "+fallback:" + fallback.provider();
  }

  @Override
  public void upsert(MemoryVectorRecord record) {
    try {
      primary.upsert(record);
    } catch (Exception e) {
      log.warn("vector upsert primary failed, continuing reason={}", e.toString());
    }
  }

  @Override
  public void upsertAll(List<MemoryVectorRecord> records) {
    try {
      primary.upsertAll(records);
    } catch (Exception e) {
      log.warn("vector upsertAll primary failed, continuing reason={}", e.toString());
    }
  }

  @Override
  public void delete(String memoryId) {
    try {
      primary.delete(memoryId);
    } catch (Exception e) {
      log.warn("vector delete primary failed id={} reason={}", memoryId, e.toString());
    }
  }

  @Override
  public void setArchived(String memoryId, String userId, boolean archived) {
    try {
      primary.setArchived(memoryId, userId, archived);
    } catch (Exception e) {
      log.warn("vector setArchived primary failed id={} reason={}", memoryId, e.toString());
    }
  }

  @Override
  public List<ScoredMemoryId> search(String userId, float[] query, int topK) {
    try {
      List<ScoredMemoryId> hits = primary.search(userId, query, topK);
      if (hits != null && !hits.isEmpty()) {
        return hits;
      }
    } catch (Exception e) {
      log.warn("vector search primary failed, fallback reason={}", e.toString());
    }
    return fallback.search(userId, query, topK);
  }

  @Override
  public VectorHealth health() {
    VectorHealth primaryHealth = primary.health();
    if (primaryHealth.ok()) {
      return new VectorHealth(
          provider(),
          true,
          primaryHealth.detail(),
          primaryHealth.collectionExists(),
          primaryHealth.pointsCount(),
          primaryHealth.configuredDimensions(),
          primaryHealth.actualDimensions());
    }
    VectorHealth fb = fallback.health();
    return new VectorHealth(
        provider(),
        fb.ok(),
        "primary: " + primaryHealth.detail() + "; fallback: " + fb.detail(),
        primaryHealth.collectionExists(),
        primaryHealth.pointsCount(),
        primaryHealth.configuredDimensions(),
        primaryHealth.actualDimensions());
  }

  @Override
  public boolean recreateIndex() {
    try {
      return primary.recreateIndex();
    } catch (Exception e) {
      log.warn("vector recreateIndex primary failed reason={}", e.toString());
      return false;
    }
  }
}
