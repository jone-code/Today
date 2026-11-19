package com.today.voice;

import com.today.media.LocalMediaStorage;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoiceTranscriptionCleanupScheduler {
  private static final Logger log = LoggerFactory.getLogger(VoiceTranscriptionCleanupScheduler.class);
  private final VoiceTranscriptionMapper mapper;
  private final LocalMediaStorage media;

  public VoiceTranscriptionCleanupScheduler(VoiceTranscriptionMapper mapper, LocalMediaStorage media) {
    this.mapper = mapper;
    this.media = media;
  }

  @Scheduled(fixedDelayString = "${today.voice.cleanup-poll-ms:3600000}")
  public void tick() {
    try {
      mapper.markExpired(Instant.now());
      for (VoiceTranscriptionJobEntity job : mapper.findExpiredWithMedia(Instant.now(), 20)) {
        if (media.deleteIfPresent(job.getMediaPath())) mapper.clearMediaPath(job.getId());
      }
    } catch (Exception failure) {
      log.warn("voice transcription cleanup scan failed errorCode={}", failure.getClass().getSimpleName());
    }
  }
}
