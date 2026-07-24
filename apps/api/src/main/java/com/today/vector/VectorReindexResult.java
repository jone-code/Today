package com.today.vector;

/** 一次 reindex / backfill 结果 */
public record VectorReindexResult(
    String scope,
    String provider,
    boolean recreate,
    boolean fillMissingEmbeddings,
    int scanned,
    int upserted,
    int embedded,
    int skippedNoVector,
    int failed) {}
