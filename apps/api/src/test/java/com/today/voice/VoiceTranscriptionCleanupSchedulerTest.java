package com.today.voice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.media.LocalMediaStorage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceTranscriptionCleanupSchedulerTest {
  private final VoiceTranscriptionMapper mapper = org.mockito.Mockito.mock(VoiceTranscriptionMapper.class);
  private final LocalMediaStorage media = org.mockito.Mockito.mock(LocalMediaStorage.class);
  private final VoiceTranscriptionCleanupScheduler scheduler =
      new VoiceTranscriptionCleanupScheduler(mapper, media);

  @Test
  void successfulDeleteClearsThePersistedPath() {
    VoiceTranscriptionJobEntity job = job("job-1", "voice/u1/audio.m4a");
    when(mapper.findExpiredWithMedia(any(Instant.class), eq(20))).thenReturn(List.of(job));
    when(media.deleteIfPresent(job.getMediaPath())).thenReturn(true);

    scheduler.tick();

    verify(mapper).markExpired(any(Instant.class));
    verify(media).deleteIfPresent("voice/u1/audio.m4a");
    verify(mapper).clearMediaPath("job-1");
  }

  @Test
  void failedDeleteKeepsPathForTheNextScan() {
    VoiceTranscriptionJobEntity job = job("job-2", "voice/u1/retry.m4a");
    when(mapper.findExpiredWithMedia(any(Instant.class), eq(20))).thenReturn(List.of(job));
    when(media.deleteIfPresent(job.getMediaPath())).thenReturn(false);

    scheduler.tick();

    verify(mapper).markExpired(any(Instant.class));
    verify(media).deleteIfPresent("voice/u1/retry.m4a");
    verify(mapper, never()).clearMediaPath("job-2");
  }

  private static VoiceTranscriptionJobEntity job(String id, String path) {
    VoiceTranscriptionJobEntity job = new VoiceTranscriptionJobEntity();
    job.setId(id); job.setMediaPath(path); job.setExpiresAt(Instant.now().minusSeconds(1));
    return job;
  }
}
