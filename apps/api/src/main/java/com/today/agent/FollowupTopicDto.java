package com.today.agent;

public record FollowupTopicDto(String id, String reason, String state,
    String nextEligibleAt, String createdAt) {}
