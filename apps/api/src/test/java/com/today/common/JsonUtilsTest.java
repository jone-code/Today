package com.today.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  @Test
  void extractJsonFromFence() {
    String raw =
        """
        这是总结：
        ```json
        {"oneLiner":"今天不错","mood":"good"}
        ```
        """;
    String json = JsonUtils.extractJson(raw);
    Map<?, ?> map = JsonUtils.fromJson(json, Map.class);
    assertEquals("今天不错", map.get("oneLiner"));
    assertEquals("good", map.get("mood"));
  }

  @Test
  void extractJsonFromProse() {
    String raw = "好的，结果如下 {\"items\":[{\"text\":\"坚持运动\"}]} 请查收";
    String json = JsonUtils.extractJson(raw);
    assertTrue(json.contains("坚持运动"));
  }
}
