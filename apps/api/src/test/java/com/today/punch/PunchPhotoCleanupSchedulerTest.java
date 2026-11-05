package com.today.punch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.today.media.LocalMediaStorage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PunchPhotoCleanupSchedulerTest {
  private final PunchPhotoCleanupMapper jobs = mock(PunchPhotoCleanupMapper.class);
  private final PunchMapper punches = mock(PunchMapper.class);
  private final LocalMediaStorage storage = mock(LocalMediaStorage.class);
  private final PunchPhotoCleanupScheduler scheduler = new PunchPhotoCleanupScheduler(jobs, punches, storage);

  @Test
  void failedDeletionIsRetriedWithoutCompletingJob() {
    when(jobs.ready(any(), eq(100))).thenReturn(List.of(job()));
    when(jobs.claim(eq("punch/u1/photo.jpg"), any(), any())).thenReturn(1);
    assertEquals(0, scheduler.processDue(100));
    verify(jobs).retry(eq("punch/u1/photo.jpg"), any(), any());
    verify(jobs, never()).complete(any(), any());
  }

  @Test
  void onlyUnreferencedPhotoCanBeRemoved() {
    when(jobs.ready(any(), eq(100))).thenReturn(List.of(job()));
    when(jobs.claim(eq("punch/u1/photo.jpg"), any(), any())).thenReturn(1);
    when(punches.isPhotoReferenced("punch/u1/photo.jpg", "u1")).thenReturn(true);
    scheduler.processDue(100);
    verifyNoInteractions(storage);
    verify(jobs, never()).complete(any(), any());
    when(punches.isPhotoReferenced("punch/u1/photo.jpg", "u1")).thenReturn(false);
    when(storage.deleteIfPresent("punch/u1/photo.jpg")).thenReturn(true);
    when(jobs.complete(eq("punch/u1/photo.jpg"), any())).thenReturn(1);
    assertEquals(1, scheduler.processDue(100));
    verify(jobs).complete(eq("punch/u1/photo.jpg"), any());
  }

  private PunchPhotoCleanupJob job() {
    PunchPhotoCleanupJob job = new PunchPhotoCleanupJob();
    job.setPath("punch/u1/photo.jpg");
    job.setUserId("u1");
    return job;
  }
}
