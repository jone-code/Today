package com.today.vector;

/**
 * 记忆向量索引（持久化外挂；不负责调用 LLM）。
 *
 * <p>embedding 仍由 {@code AiGatewayService.embed} 生成；本接口只负责写入与 Top-K 检索。
 */
public interface VectorStore {

  /** mysql | qdrant */
  String provider();

  void upsert(MemoryVectorRecord record);

  void upsertAll(java.util.List<MemoryVectorRecord> records);

  void delete(String memoryId);

  /** 更新归档等元数据；无向量时实现可 no-op */
  void setArchived(String memoryId, String userId, boolean archived);

  /**
   * 按用户检索最相关记忆 id（默认应排除 archived）。
   *
   * @return 空列表表示无命中或不可用（调用方应降级）
   */
  java.util.List<ScoredMemoryId> search(String userId, float[] query, int topK);
}
