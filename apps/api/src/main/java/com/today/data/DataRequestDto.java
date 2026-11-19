package com.today.data;

public record DataRequestDto(String id, String type, String status, String requestedAt,
    String completedAt, String expiresAt, boolean retryable, String downloadToken) {}
