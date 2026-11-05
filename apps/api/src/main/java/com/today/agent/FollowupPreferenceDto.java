package com.today.agent;

public record FollowupPreferenceDto(boolean enabled, int maxPerDay, int maxPerWeek, String timezone) {}
