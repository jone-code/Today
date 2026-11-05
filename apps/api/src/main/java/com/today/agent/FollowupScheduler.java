package com.today.agent;

import com.today.common.EntityMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FollowupScheduler {
  private final FollowupService followups;
  public FollowupScheduler(FollowupService followups) { this.followups = followups; }
  @Scheduled(fixedDelayString = "${today.agent.followup-poll-ms:60000}")
  public void tick() { followups.dispatchDue(EntityMapper.now()); }
}
