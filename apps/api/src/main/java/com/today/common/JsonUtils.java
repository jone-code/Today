package com.today.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern FENCE =
      Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  private JsonUtils() {}

  public static String toJson(List<String> values) {
    try {
      return MAPPER.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize json list", e);
    }
  }

  public static List<String> fromJsonList(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize json list", e);
    }
  }

  public static <T> T fromJson(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize json", e);
    }
  }

  public static <T> T fromJson(String json, TypeReference<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize json", e);
    }
  }

  public static String toJsonFloatArray(float[] values) {
    try {
      return MAPPER.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize float array", e);
    }
  }

  public static float[] fromJsonFloatArray(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(json, float[].class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize float array", e);
    }
  }

  /** 从模型输出中抽出 JSON 对象/数组（容忍 markdown fence 与前后废话） */
  public static String extractJson(String content) {
    if (content == null) {
      throw new IllegalArgumentException("content is null");
    }
    String trimmed = content.trim();
    Matcher fence = FENCE.matcher(trimmed);
    if (fence.find()) {
      trimmed = fence.group(1).trim();
    }

    int obj = trimmed.indexOf('{');
    int arr = trimmed.indexOf('[');
    int start;
    if (obj < 0 && arr < 0) {
      throw new IllegalArgumentException("no json object/array found in model output");
    } else if (obj < 0) {
      start = arr;
    } else if (arr < 0) {
      start = obj;
    } else {
      start = Math.min(obj, arr);
    }

    String slice = trimmed.substring(start);
    try {
      JsonNode node = MAPPER.readTree(slice);
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException first) {
      // 尝试从尾部截到匹配括号
      char open = slice.charAt(0);
      char close = open == '{' ? '}' : ']';
      int depth = 0;
      int end = -1;
      for (int i = 0; i < slice.length(); i++) {
        char c = slice.charAt(i);
        if (c == open) {
          depth++;
        } else if (c == close) {
          depth--;
          if (depth == 0) {
            end = i;
            break;
          }
        }
      }
      if (end < 0) {
        throw new IllegalArgumentException("invalid json in model output", first);
      }
      try {
        JsonNode node = MAPPER.readTree(slice.substring(0, end + 1));
        return MAPPER.writeValueAsString(node);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("invalid json in model output", e);
      }
    }
  }
}
