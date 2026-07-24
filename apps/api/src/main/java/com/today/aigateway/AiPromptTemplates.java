package com.today.aigateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.today.aigateway.AiGatewayService.AiTask;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplates {

  private final ObjectMapper mapper;

  public AiPromptTemplates(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String system(AiTask task) {
    return switch (task) {
      case summary ->
          """
          你是「Today」的每日总结助手。根据用户今日记录，输出严格 JSON（不要 markdown）：
          {
            "completed": ["完成事项1", "完成事项2"],
            "mood": "great|good|okay|tired|low",
            "moodLabel": "中文情绪标签",
            "keywords": ["关键词", "...最多4个"],
            "oneLiner": "一句温和的总结",
            "highlight": "今天最值得记住的一点"
          }
          规则：语气温暖、具体、不说教；completed 1~3 条；keywords 1~4 个；只用给定 mood 枚举。
          """;
      case memory_extract ->
          """
          你是「Today」的长期记忆抽取助手。从用户今日记录中抽取可跨日复用的记忆，输出严格 JSON：
          {
            "items": [
              {"category": "work|health|learning|life|emotion|goal", "text": "简短记忆陈述"}
            ]
          }
          规则：只保留对未来有用的稳定信息；没有则 items 为空数组；text 用中文、一句说清；最多 5 条。
          """;
      case proactive ->
          """
          你是「Today」的主动关联助手。输入里的 memories 已是与今日最相关的检索结果（不是全量）。
          结合近期记录与这些候选记忆，生成今日开场/追问，输出严格 JSON：
          {
            "prompts": [
              {
                "id": "短横线风格唯一 id",
                "text": "给用户看的中文提示",
                "relatedDate": "YYYY-MM-DD 或 null",
                "source": "followup|pattern|memory|gentle"
              }
            ]
          }
          规则：最多 3 条；优先 followup（追问昨日安排）与 pattern（重复情绪/行为）；
          可引用候选记忆但不要编造不存在的事实；语气像记得对方的朋友。
          """;
    };
  }

  @SuppressWarnings("unchecked")
  public String user(AiTask task, Object input) {
    Map<String, Object> map =
        input instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of("input", input);
    try {
      return switch (task) {
        case summary -> "今日记录：\n" + stringVal(map, "rawText");
        case memory_extract -> "今日记录：\n" + stringVal(map, "rawText");
        case proactive ->
            """
            今日日期：%s

            近期记录（JSON）：
            %s

            检索到的相关记忆候选（JSON，已按相关度排序）：
            %s
            """
                .formatted(
                    stringVal(map, "date"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map.get("recent")),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map.get("memories")));
      };
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to build prompt input", e);
    }
  }

  private static String stringVal(Map<String, Object> map, String key) {
    Object v = map.get(key);
    return v == null ? "" : String.valueOf(v);
  }
}
