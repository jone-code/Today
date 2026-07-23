package com.today.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VectorMathTest {

  @Test
  void identicalVectorsScoreOne() {
    float[] a = {1f, 0f, 0f};
    assertEquals(1.0, VectorMath.cosineSimilarity(a, a), 1e-6);
  }

  @Test
  void orthogonalVectorsScoreZero() {
    float[] a = {1f, 0f};
    float[] b = {0f, 1f};
    assertEquals(0.0, VectorMath.cosineSimilarity(a, b), 1e-6);
  }

  @Test
  void closerVectorRanksHigher() {
    float[] query = {1f, 1f, 0f};
    float[] near = {0.9f, 1.1f, 0.05f};
    float[] far = {0f, 0f, 1f};
    assertTrue(VectorMath.cosineSimilarity(query, near) > VectorMath.cosineSimilarity(query, far));
  }
}
