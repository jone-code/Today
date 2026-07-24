package com.today.vector;

/** 写入向量索引的一条记忆 */
public record MemoryVectorRecord(
    String memoryId,
    String userId,
    String category,
    String text,
    int strength,
    boolean archived,
    float[] vector) {}
