package com.today.todo;

public record TodoDto(
    String id,
    String userId,
    String title,
    String note,
    String status,
    String dueDate,
    String createdAt,
    String updatedAt,
    String completedAt) {}
