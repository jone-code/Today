package com.today.vector;

import java.util.LinkedHashMap;
import java.util.Map;

/** 向量索引健康快照（供 /health 与运维） */
public record VectorHealth(
    String provider,
    boolean ok,
    String detail,
    Boolean collectionExists,
    Long pointsCount,
    Integer configuredDimensions,
    Integer actualDimensions) {

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("provider", provider);
    m.put("ok", ok);
    m.put("detail", detail);
    if (collectionExists != null) m.put("collectionExists", collectionExists);
    if (pointsCount != null) m.put("pointsCount", pointsCount);
    if (configuredDimensions != null) m.put("configuredDimensions", configuredDimensions);
    if (actualDimensions != null) m.put("actualDimensions", actualDimensions);
    return m;
  }

  public static VectorHealth mysqlOk(long withEmbedding) {
    return new VectorHealth(
        "mysql", true, "scanning embedding_json", null, withEmbedding, null, null);
  }

  public static VectorHealth unavailable(String provider, String detail) {
    return new VectorHealth(provider, false, detail, null, null, null, null);
  }
}
