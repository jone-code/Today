package com.today.agent;

import com.today.media.LocalMediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentMediaCleanupScheduler {
  private static final Logger log = LoggerFactory.getLogger(AgentMediaCleanupScheduler.class);
  private final AgentMediaMapper media;
  private final LocalMediaStorage storage;

  public AgentMediaCleanupScheduler(AgentMediaMapper media, LocalMediaStorage storage) {
    this.media = media;
    this.storage = storage;
  }

  @Scheduled(fixedDelayString = "${today.agent.media-cleanup-ms:3600000}")
  public void purgeReplaced() {
    for (var item : media.listReplaced(100)) {
      try {
        if (storage.deleteIfPresent(item.getRelativePath())) {
          media.markPurged(item.getId(), item.getUserId());
        } else {
          media.deferCleanup(item.getId(), item.getUserId());
        }
      } catch (RuntimeException e) {
        log.warn("avatar cleanup failed mediaId={} errorCode={}", item.getId(), e.getClass().getSimpleName());
        try {
          media.deferCleanup(item.getId(), item.getUserId());
        } catch (RuntimeException deferError) {
          log.warn("avatar cleanup retry scheduling failed mediaId={} errorCode={}",
              item.getId(), deferError.getClass().getSimpleName());
        }
      }
    }
  }
}
