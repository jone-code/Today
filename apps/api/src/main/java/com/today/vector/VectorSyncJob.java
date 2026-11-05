package com.today.vector;

public record VectorSyncJob(String memoryId, String userId, long version, int attempts) {}
