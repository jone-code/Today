package com.today.memory;

public record MemoryAccessAuditDto(String id, String memoryId, String action, String source, String occurredAt) {}
