package com.today.reminder;

public record ReminderDeliveryDto(
    String id,
    String reminderId,
    String userId,
    String fireDate,
    String title,
    String message,
    String status,
    String createdAt,
    String readAt) {}
