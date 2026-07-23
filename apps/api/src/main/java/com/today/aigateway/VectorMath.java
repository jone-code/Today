package com.today.aigateway;

/** 内存向量相似度（MVP；记忆量级可控，无需独立向量库） */
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
