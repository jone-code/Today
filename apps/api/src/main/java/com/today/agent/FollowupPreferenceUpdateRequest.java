package com.today.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FollowupPreferenceUpdateRequest(
    Boolean enabled,
    @Min(0) @Max(1) Integer maxPerDay,
    @Min(0) @Max(3) Integer maxPerWeek,
    String timezone) {}
