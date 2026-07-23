package com.today.reminder;

public record ReminderDto(
    String id,
    String userId,
    String title,
    String message,
    String remindTime,
    String timezone,
    boolean enabled,
    String createdAt,
    String updatedAt) {}
