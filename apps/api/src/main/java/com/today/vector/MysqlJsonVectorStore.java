package com.today.vector;

import com.today.aigateway.VectorMath;
import com.today.common.JsonUtils;
import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 默认实现：从 MySQL {@code memories.embedding_json} 全量加载后余弦 Top-K。
 *
 * <p>upsert/delete/archive 均为 no-op（MySQL 行由 MemoryService 维护）。
 */
@Component
public class MysqlJsonVectorStore implements VectorStore {

  private final MemoryMapper memoryMapper;

  public MysqlJsonVectorStore(MemoryMapper memoryMapper) {
    this.memoryMapper = memoryMapper;
  }

  @Override
  public String provider() {
    return "mysql";
  }

  @Override
  public void upsert(MemoryVectorRecord record) {
    // MySQL embedding_json 已由 MemoryService 写入
  }

  @Override
  public void upsertAll(List<MemoryVectorRecord> records) {
    // no-op
  }

  @Override
  public void delete(String memoryId) {
    // no-op
  }

  @Override
  public void setArchived(String memoryId, String userId, boolean archived) {
    // no-op
  }

  @Override
  public List<ScoredMemoryId> search(String userId, float[] query, int topK) {
    if (query == null || query.length == 0 || userId == null) {
      return List.of();
    }
    List<MemoryEntity> all = memoryMapper.listByUserId(userId, false);
    List<ScoredMemoryId> scored = new ArrayList<>();
    for (MemoryEntity entity : all) {
      float[] vec = JsonUtils.fromJsonFloatArray(entity.getEmbeddingJson());
      if (vec == null) {
        continue;
      }
      double score = VectorMath.cosineSimilarity(query, vec);
      if (score == Double.NEGATIVE_INFINITY) {
        continue;
      }
      scored.add(new ScoredMemoryId(entity.getId(), score));
    }
    int limit = Math.max(1, topK);
    return scored.stream()
        .sorted(Comparator.comparingDouble(ScoredMemoryId::score).reversed())
        .limit(limit)
        .toList();
  }

  @Override
  public VectorHealth health() {
    long withEmbedding = memoryMapper.countWithEmbedding();
    return VectorHealth.mysqlOk(withEmbedding);
  }
}
