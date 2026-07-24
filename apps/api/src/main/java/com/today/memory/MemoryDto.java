package com.today.memory;

import com.today.common.MemoryCategory;

public record MemoryDto(
    String id,
    String userId,
    MemoryCategory category,
    String text,
    int strength,
    boolean archived,
    String updatedAt) {}
