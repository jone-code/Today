package com.today.checkin;

public record CheckinAiJobDto(
    String id,
    String checkinId,
    String checkinDate,
    String status,
    int attempts,
    int maxAttempts,
    String lastError) {}
