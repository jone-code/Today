package com.today.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowupTopicUpdateRequest(
    @NotBlank @Size(max = 200) String reason,
    String nextDate,
    @NotBlank @Size(max = 512) String expectedReason,
    @NotBlank @Size(max = 64) String expectedNextEligibleAt) {}
