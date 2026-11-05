package com.today.vector;

import com.today.aigateway.AiGatewayService;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.identity.IdentityService;
import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 存量记忆 → embedding 补齐 → VectorStore 同步 */
@Service
public class VectorReindexService {

  private static final Logger log = LoggerFactory.getLogger(VectorReindexService.class);
  private static final int PAGE = 64;
  private static final int UPSERT_BATCH = 32;

  private final MemoryMapper memoryMapper;
  private final AiGatewayService aiGateway;
  private final VectorStore vectorStore;
  private final IdentityService identity;
  private final VectorSyncJobMapper vectorJobs;

  public VectorReindexService(
      MemoryMapper memoryMapper,
      AiGatewayService aiGateway,
      VectorStore vectorStore,
      IdentityService identity,
      VectorSyncJobMapper vectorJobs) {
    this.memoryMapper = memoryMapper;
    this.aiGateway = aiGateway;
    this.vectorStore = vectorStore;
    this.identity = identity;
    this.vectorJobs = vectorJobs;
  }

  public VectorHealth health() {
    return vectorStore.health();
  }

  /** 当前用户：把 MySQL 记忆同步到向量索引；可选补齐缺失 embedding */
  public VectorReindexResult reindexCurrentUser(boolean fillMissingEmbeddings, boolean recreate) {
    String userId = identity.getCurrentUserId();
    return reindex(userId, fillMissingEmbeddings, recreate);
  }

  /** 全量（所有用户） */
  public VectorReindexResult reindexAll(boolean fillMissingEmbeddings, boolean recreate) {
    return reindex(null, fillMissingEmbeddings, recreate);
  }

  private VectorReindexResult reindex(
      String userIdOrNull, boolean fillMissingEmbeddings, boolean recreate) {
    if (recreate) {
      boolean did = vectorStore.recreateIndex();
      log.info("vector recreateIndex provider={} did={}", vectorStore.provider(), did);
    }

    int scanned = 0;
    int upserted = 0;
    int embedded = 0;
    int skipped = 0;
    int failed = 0;
    int offset = 0;

    while (true) {
      List<MemoryEntity> page =
          userIdOrNull == null
              ? memoryMapper.listPage(PAGE, offset)
              : memoryMapper.listPageByUserId(userIdOrNull, PAGE, offset);
      if (page.isEmpty()) {
        break;
      }
      scanned += page.size();

      List<MemoryEntity> eligible = new ArrayList<>();
      for (MemoryEntity entity : page) {
        MemoryEntity current = memoryMapper.findConfirmedById(entity.getId(), entity.getUserId());
        if (current == null) {
          vectorJobs.enqueue(entity.getId(), entity.getUserId(), EntityMapper.now());
          skipped++;
        } else {
          eligible.add(current);
        }
      }

      if (fillMissingEmbeddings) {
        embedded += fillMissing(eligible);
      }

      List<MemoryVectorRecord> batch = new ArrayList<>();
      for (MemoryEntity before : eligible) {
        MemoryEntity entity = memoryMapper.findConfirmedById(before.getId(), before.getUserId());
        float[] vec = entity == null ? null : JsonUtils.fromJsonFloatArray(entity.getEmbeddingJson());
        if (vec == null || vec.length == 0) {
          vectorJobs.enqueue(before.getId(), before.getUserId(), EntityMapper.now());
          skipped++;
          continue;
        }
        batch.add(
            new MemoryVectorRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getCategory(),
                entity.getMemoryText(),
                entity.getStrength(),
                entity.isArchived(),
                vec));
        if (batch.size() >= UPSERT_BATCH) {
          int fail = flush(batch);
          if (fail == 0) {
            upserted += batch.size();
          } else {
            failed += fail;
          }
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        int fail = flush(batch);
        if (fail == 0) {
          upserted += batch.size();
        } else {
          failed += fail;
        }
      }

      offset += page.size();
      if (page.size() < PAGE) {
        break;
      }
    }

    String scope = userIdOrNull == null ? "all" : "user";
    return new VectorReindexResult(
        scope,
        vectorStore.provider(),
        recreate,
        fillMissingEmbeddings,
        scanned,
        upserted,
        embedded,
        skipped,
        failed);
  }

  private int fillMissing(List<MemoryEntity> page) {
    List<MemoryEntity> need = new ArrayList<>();
    for (MemoryEntity e : page) {
      if (!e.isArchived() && (e.getEmbeddingJson() == null || e.getEmbeddingJson().isBlank())) {
        need.add(e);
      }
    }
    if (need.isEmpty()) {
      return 0;
    }
    List<String> texts = need.stream().map(MemoryEntity::getMemoryText).toList();
    Optional<List<float[]>> vectors = aiGateway.embed(texts);
    if (vectors.isEmpty()) {
      log.warn("reindex fillMissing: embed unavailable, skip {}", need.size());
      return 0;
    }
    Instant now = EntityMapper.now();
    List<float[]> vecs = vectors.get();
    int updated = 0;
    for (int i = 0; i < need.size(); i++) {
      MemoryEntity entity = need.get(i);
      updated += memoryMapper.updateEmbeddingIfConfirmed(entity.getId(), entity.getUserId(),
          entity.getMemoryText(), entity.getUpdatedAt(), JsonUtils.toJsonFloatArray(vecs.get(i)), now);
    }
    return updated;
  }

  private int flush(List<MemoryVectorRecord> batch) {
    try {
      vectorStore.upsertAll(List.copyOf(batch));
      return 0;
    } catch (Exception e) {
      log.warn("reindex upsert batch failed size={} errorCode={}", batch.size(), e.getClass().getSimpleName());
      return batch.size();
    } finally {
      for (MemoryVectorRecord record : batch) {
        vectorJobs.enqueue(record.memoryId(), record.userId(), EntityMapper.now());
      }
    }
  }
}
