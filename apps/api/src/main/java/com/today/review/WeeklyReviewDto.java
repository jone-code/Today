package com.today.review;

import java.time.Instant;
import java.util.List;

public record WeeklyReviewDto(String week, String status, int attempts, String error,
    int daysRecorded, int completedTodos, List<String> highlights, String reflection,
    Instant generatedAt, boolean retryable) {}
