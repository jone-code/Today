package com.today.vector;

/** 向量检索命中（memoryId + 相似度） */
public record ScoredMemoryId(String memoryId, double score) {}
