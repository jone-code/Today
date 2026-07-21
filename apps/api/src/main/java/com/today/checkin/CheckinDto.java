package com.today.checkin;

public record CheckinDto(
    String id,
    String userId,
    String date,
    String rawText,
    String createdAt,
    String updatedAt) {}
