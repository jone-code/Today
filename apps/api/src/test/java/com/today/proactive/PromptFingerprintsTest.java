package com.today.proactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.today.common.PromptSource;
import org.junit.jupiter.api.Test;

class PromptFingerprintsTest {

  @Test
  void fingerprintStableForSamePrompt() {
    ProactivePromptDto a =
        new ProactivePromptDto("followup-interview", "之前提到面试，结果怎么样？", "2026-07-23", PromptSource.followup);
    ProactivePromptDto b =
        new ProactivePromptDto("followup-interview", "之前提到面试，结果怎么样？", "2026-07-23", PromptSource.followup);
    assertEquals(PromptFingerprints.of(a), PromptFingerprints.of(b));
  }

  @Test
  void followupTopicFingerprintDiffersByDate() {
    assertTrue(
        !PromptFingerprints.ofFollowupTopic("interview", "2026-07-22")
            .equals(PromptFingerprints.ofFollowupTopic("interview", "2026-07-23")));
  }

  @Test
  void sourceRankPrefersFollowup() {
    assertTrue(
        PromptFingerprints.sourceRank(PromptSource.followup)
            < PromptFingerprints.sourceRank(PromptSource.gentle));
  }
}
