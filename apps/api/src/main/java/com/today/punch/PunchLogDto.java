package com.today.punch;

public record PunchLogDto(
    String id,
    String habitId,
    String userId,
    String punchDate,
    String note,
    String createdAt) {}
