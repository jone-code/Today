package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.memory.MemoryMapper;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VectorSyncSchedulerTest {

  @Test
  void deletedMemoryIsRemovedFromQdrantOnlyAfterJobClaim() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemoryMapper memories = mock(MemoryMapper.class);
    QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
    VectorProperties props = qdrantProperties();
    VectorSyncJob job = new VectorSyncJob("m1", "u1", 4, 0);
    when(jobs.ready(any(Instant.class), eq(50))).thenReturn(List.of(job));
    when(jobs.claim(eq("m1"), eq("u1"), eq(4L), any(), any())).thenReturn(1);
    when(jobs.complete("m1", "u1", 5)).thenReturn(1);

    assertEquals(1, new VectorSyncScheduler(jobs, memories, props, qdrant).processDue(50));

    verify(qdrant).delete("m1");
    verify(jobs).complete("m1", "u1", 5);
  }

  @Test
  void qdrantFailureRetainsJobForRetryWithoutAcknowledging() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemoryMapper memories = mock(MemoryMapper.class);
    QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
    VectorSyncJob job = new VectorSyncJob("m1", "u1", 4, 0);
    when(jobs.ready(any(Instant.class), eq(50))).thenReturn(List.of(job));
    when(jobs.claim(eq("m1"), eq("u1"), eq(4L), any(), any())).thenReturn(1);
    org.mockito.Mockito.doThrow(new IllegalStateException("sensitive text"))
        .when(qdrant).delete("m1");

    assertEquals(0, new VectorSyncScheduler(jobs, memories, qdrantProperties(), qdrant).processDue(50));

    verify(jobs).retry(eq("m1"), eq("u1"), eq(5L), any(), eq("IllegalStateException"));
    verify(jobs, never()).complete(eq("m1"), eq("u1"), anyLong());
  }

  @Test
  void restoredMemoryIsUpsertedInsteadOfDeleted() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemoryMapper memories = mock(MemoryMapper.class);
    QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
    VectorSyncJob job = new VectorSyncJob("m1", "u1", 7, 2);
    MemoryEntity active = new MemoryEntity();
    active.setId("m1"); active.setUserId("u1"); active.setCategory("life");
    active.setMemoryText("confirmed"); active.setEmbeddingJson("[1,0]");
    when(jobs.ready(any(Instant.class), eq(50))).thenReturn(List.of(job));
    when(jobs.claim(eq("m1"), eq("u1"), eq(7L), any(), any())).thenReturn(1);
    when(memories.findConfirmedById("m1", "u1")).thenReturn(active);

    new VectorSyncScheduler(jobs, memories, qdrantProperties(), qdrant).processDue(50);

    ArgumentCaptor<MemoryVectorRecord> record = ArgumentCaptor.forClass(MemoryVectorRecord.class);
    verify(qdrant).upsert(record.capture());
    assertEquals("confirmed", record.getValue().text());
    verify(qdrant, never()).delete("m1");
  }

  @Test
  void staleWorkerRequeuesLatestStateWhenNewVersionAlreadyCompleted() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemoryMapper memories = mock(MemoryMapper.class);
    QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
    VectorSyncJob old = new VectorSyncJob("m1", "u1", 3, 0);
    when(jobs.ready(any(Instant.class), eq(50))).thenReturn(List.of(old));
    when(jobs.claim(eq("m1"), eq("u1"), eq(3L), any(), any())).thenReturn(1);
    when(jobs.complete("m1", "u1", 4)).thenReturn(0);

    new VectorSyncScheduler(jobs, memories, qdrantProperties(), qdrant).processDue(50);

    verify(qdrant).delete("m1");
    verify(jobs).enqueue(eq("m1"), eq("u1"), any(Instant.class));
  }

  @Test
  void sourceInvalidatedBeforeSyncRemovesStaleVector() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemoryMapper memories = mock(MemoryMapper.class);
    QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
    VectorSyncJob job = new VectorSyncJob("private", "u1", 1, 0);
    when(jobs.ready(any(Instant.class), eq(50))).thenReturn(List.of(job));
    when(jobs.claim(eq("private"), eq("u1"), eq(1L), any(), any())).thenReturn(1);
    when(jobs.complete("private", "u1", 2)).thenReturn(1);

    new VectorSyncScheduler(jobs, memories, qdrantProperties(), qdrant).processDue(50);

    verify(memories).findConfirmedById("private", "u1");
    verify(qdrant).delete("private");
    verify(qdrant, never()).upsert(any());
  }

  @Test
  void mysqlModeCannotAcknowledgePendingQdrantCleanup() {
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    VectorProperties props = new VectorProperties();
    props.setProvider("mysql");

    assertEquals(0, new VectorSyncScheduler(jobs, mock(MemoryMapper.class), props,
        mock(QdrantVectorStore.class)).processDue(50));

    org.mockito.Mockito.verifyNoInteractions(jobs);
  }

  private VectorProperties qdrantProperties() {
    VectorProperties props = new VectorProperties();
    props.setProvider("qdrant");
    return props;
  }
}
