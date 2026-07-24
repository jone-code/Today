package com.today.aigateway;

/** 向量余弦相似度（MysqlJsonVectorStore / 单测共用） */
public final class VectorMath {

  private VectorMath() {}

  public static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null || a.length == 0 || a.length != b.length) {
      return Double.NEGATIVE_INFINITY;
    }
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    if (na == 0 || nb == 0) {
      return Double.NEGATIVE_INFINITY;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
