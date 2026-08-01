package com.today.punch;

public record PunchHabitDto(
    String id,
    String userId,
    String title,
    String description,
    boolean enabled,
    String createdAt,
    String updatedAt,
    boolean punchedToday,
    int streak,
    String todayPhotoUrl) {}
