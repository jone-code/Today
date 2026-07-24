package com.today.proactive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.common.PromptSource;
import com.today.memory.MemoryDto;
import com.today.memory.MemoryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProactiveService {

  private static final TypeReference<ProactivePayload> PROACTIVE_TYPE =
      new TypeReference<>() {};

  private final AiGatewayService aiGateway;
  private final CheckinService checkins;
  private final MemoryService memories;

  public ProactiveService(
      AiGatewayService aiGateway, CheckinService checkins, MemoryService memories) {
    this.aiGateway = aiGateway;
    this.checkins = checkins;
    this.memories = memories;
  }

  public ProactiveTodayDto today() {
    String date = checkins.todayDate();
    List<CheckinDto> recent = checkins.listRecent(14);
    List<CheckinDto> recentForPrompt =
        recent.size() > 7 ? recent.subList(0, 7) : recent;

    String query = buildRetrievalQuery(date, recent);
    List<MemoryDto> retrieved = memories.retrieveRelevant(query, aiGateway.retrieveTopK());

    Map<String, Object> input = new HashMap<>();
    input.put("date", date);
    input.put("recent", recentForPrompt);
    input.put("memories", retrieved);

    var result =
        aiGateway.complete(
            AiTask.proactive,
            input,
            PROACTIVE_TYPE,
            () -> new ProactivePayload(heuristicPrompts(date, recent, retrieved)));

    List<ProactivePromptDto> prompts =
        result.data() == null || result.data().prompts() == null
            ? List.of()
            : result.data().prompts().stream()
                .filter(p -> p != null && p.text() != null && !p.text().isBlank())
                .map(this::normalizePrompt)
                .limit(3)
                .toList();

    if (prompts.isEmpty()) {
      prompts = heuristicPrompts(date, recent, retrieved);
    }

    return new ProactiveTodayDto(date, prompts, result.provider());
  }

  private String buildRetrievalQuery(String today, List<CheckinDto> recent) {
    String recentText =
        recent.stream()
            .limit(3)
            .map(c -> c.date() + "：" + c.rawText())
            .collect(Collectors.joining("\n"));
    if (recentText.isBlank()) {
      return "今天是 " + today + "。请根据用户长期记忆，温和地开启今日对话。";
    }
    return "今天是 " + today + "。近期记录：\n" + recentText;
  }

  private ProactivePromptDto normalizePrompt(ProactivePromptDto p) {
    String id =
        p.id() == null || p.id().isBlank()
            ? "prompt-" + Integer.toHexString(p.text().hashCode())
            : p.id().trim();
    PromptSource source = p.source() == null ? PromptSource.gentle : p.source();
    String related =
        p.relatedDate() == null || p.relatedDate().isBlank() ? null : p.relatedDate().trim();
    return new ProactivePromptDto(id, p.text().trim(), related, source);
  }

  private List<ProactivePromptDto> heuristicPrompts(
      String today, List<CheckinDto> recent, List<MemoryDto> memoryList) {
    List<ProactivePromptDto> prompts = new ArrayList<>();
    LocalDate todayDate = LocalDate.parse(today);
    String yesterday = todayDate.minusDays(1).toString();

    CheckinDto y =
        recent.stream().filter(e -> e.date().equals(yesterday)).findFirst().orElse(null);

    if (y != null && y.rawText().matches(".*面试.*")) {
      prompts.add(
          new ProactivePromptDto(
              "followup-interview",
              "昨天提到今天有面试，结果怎么样？",
              yesterday,
              PromptSource.followup));
    } else if (y != null && y.rawText().matches(".*(明天|准备).*")) {
      prompts.add(
          new ProactivePromptDto(
              "followup-plan",
              "昨天你提到了今天的计划，现在进展如何？",
              yesterday,
              PromptSource.followup));
    }

    long tiredCount =
        recent.stream().filter(e -> e.rawText().matches(".*(很累|疲惫|累).*")).count();
    if (tiredCount >= 3) {
      prompts.add(
          new ProactivePromptDto(
              "pattern-tired",
              "最近两周你已经有 " + tiredCount + " 天提到“累”，我在陪你留意这件事。",
              null,
              PromptSource.pattern));
    }

    if (!memoryList.isEmpty()) {
      MemoryDto top = memoryList.get(0);
      if (prompts.size() < 2) {
        prompts.add(
            new ProactivePromptDto(
                "memory-" + top.id(),
                "我还记得：" + top.text() + "。今天有没有新的变化？",
                null,
                PromptSource.memory));
      }
    }

    if (prompts.isEmpty()) {
      prompts.add(
          new ProactivePromptDto(
              "gentle-checkin", "我在这里。用几句话留下今天就好。", null, PromptSource.gentle));
    }

    return prompts;
  }

  public record ProactivePayload(List<ProactivePromptDto> prompts) {}
}
