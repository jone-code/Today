package com.today.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QdrantClientTest {

  @Test
  void pointIdIsStableAndDistinct() {
    assertEquals(QdrantClient.pointId("a"), QdrantClient.pointId("a"));
    assertNotEquals(QdrantClient.pointId("a"), QdrantClient.pointId("b"));
  }
}
