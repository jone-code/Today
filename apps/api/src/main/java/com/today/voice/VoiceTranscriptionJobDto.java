package com.today.voice;

import java.time.Instant;

public record VoiceTranscriptionJobDto(String id, String status, String transcript, String errorCode,
    int attempts, Instant createdAt, Instant updatedAt, Instant expiresAt, Instant completedAt, boolean retryable) {}
