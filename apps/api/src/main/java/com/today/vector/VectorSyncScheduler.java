package com.today.vector;

import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VectorSyncScheduler {
  private static final Logger log = LoggerFactory.getLogger(VectorSyncScheduler.class);

  private final VectorSyncJobMapper jobs;
  private final MemoryMapper memories;
  private final VectorProperties properties;
  private final QdrantVectorStore qdrant;

  public VectorSyncScheduler(VectorSyncJobMapper jobs, MemoryMapper memories,
      VectorProperties properties, QdrantVectorStore qdrant) {
    this.jobs = jobs;
    this.memories = memories;
    this.properties = properties;
    this.qdrant = qdrant;
  }

  @Scheduled(fixedDelayString = "${today.vector.sync-poll-ms:15000}")
  public void tick() {
    try {
      processDue(50);
    } catch (RuntimeException e) {
      log.warn("vector sync scan failed errorCode={}", e.getClass().getSimpleName());
    }
  }

  public int processDue(int limit) {
    if (!properties.isQdrant()) return 0;
    int completed = 0;
    Instant now = EntityMapper.now();
    for (VectorSyncJob ready : jobs.ready(now, Math.min(100, Math.max(1, limit)))) {
      if (jobs.claim(ready.memoryId(), ready.userId(), ready.version(), now, now.plusSeconds(120)) != 1) {
        continue;
      }
      long claimedVersion = ready.version() + 1;
      try {
        syncQdrant(ready);
        int acknowledged = jobs.complete(ready.memoryId(), ready.userId(), claimedVersion);
        if (acknowledged == 0) {
          jobs.enqueue(ready.memoryId(), ready.userId(), EntityMapper.now());
        }
        completed += acknowledged;
      } catch (RuntimeException e) {
        int attempts = Math.min(ready.attempts() + 1, 10);
        long delaySeconds = Math.min(3600, 5L << attempts);
        int retried = jobs.retry(ready.memoryId(), ready.userId(), claimedVersion,
            EntityMapper.now().plusSeconds(delaySeconds), e.getClass().getSimpleName());
        if (retried == 0) jobs.enqueue(ready.memoryId(), ready.userId(), EntityMapper.now());
        log.warn("vector sync will retry memoryId={} errorCode={}",
            ready.memoryId(), e.getClass().getSimpleName());
      }
    }
    return completed;
  }

  private void syncQdrant(VectorSyncJob job) {
    MemoryEntity memory = memories.findConfirmedById(job.memoryId(), job.userId());
    if (memory == null) {
      qdrant.delete(job.memoryId());
      return;
    }
    float[] embedding = JsonUtils.fromJsonFloatArray(memory.getEmbeddingJson());
    if (embedding == null || embedding.length == 0) {
      qdrant.delete(job.memoryId());
      return;
    }
    qdrant.upsert(new MemoryVectorRecord(memory.getId(), memory.getUserId(), memory.getCategory(),
        memory.getMemoryText(), memory.getStrength(), false, embedding));
  }
}
