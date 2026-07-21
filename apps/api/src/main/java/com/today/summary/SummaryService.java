package com.today.summary;

import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.AiProvider;
import com.today.common.Mood;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SummaryService {

  private static final Pattern SPLIT =
      Pattern.compile("[。！？!?\\n；;]+");
  private static final List<String> KEYWORD_CANDIDATES =
      List.of("工作", "学习", "家庭", "运动", "创业", "面试");

  private final Map<String, DaySummaryDto> store = new ConcurrentHashMap<>();
  private final AiGatewayService aiGateway;

  public SummaryService(AiGatewayService aiGateway) {
    this.aiGateway = aiGateway;
  }

  public DaySummaryDto generateForCheckin(String checkinId, String date, String rawText) {
    var result =
        aiGateway.complete(AiTask.summary, Map.of("rawText", rawText), () -> heuristicSummary(rawText));

    DaySummaryDto dto =
        new DaySummaryDto(
            checkinId,
            date,
            result.data().completed(),
            result.data().mood(),
            result.data().moodLabel(),
            result.data().keywords(),
            result.data().oneLiner(),
            result.data().highlight(),
            result.provider(),
            Instant.now().toString());

    store.put(date, dto);
    return dto;
  }

  public DaySummaryDto getByDate(String date) {
    DaySummaryDto found = store.get(date);
    if (found == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "summary not found: " + date);
    }
    return found;
  }

  private SummaryPayload heuristicSummary(String rawText) {
    String text = rawText.trim();
    Mood mood =
        text.matches(".*(累|疲惫|加班).*")
            ? Mood.tired
            : text.matches(".*(开心|顺利|收获).*") ? Mood.great : Mood.okay;
    String moodLabel =
        mood == Mood.tired ? "疲惫" : mood == Mood.great ? "很好" : "平常";

    List<String> keywords = new ArrayList<>();
    for (String k : KEYWORD_CANDIDATES) {
      if (text.contains(k)) {
        keywords.add(k);
      }
    }
    if (keywords.isEmpty()) {
      keywords.add("日常");
    } else if (keywords.size() > 4) {
      keywords = keywords.subList(0, 4);
    }

    List<String> parts = new ArrayList<>();
    for (String part : SPLIT.split(text)) {
      String s = part.trim();
      if (s.length() >= 4) {
        parts.add(s);
      }
    }

    List<String> completed = parts.isEmpty() ? List.of("记录了今天的片刻") : parts.subList(0, Math.min(2, parts.size()));
    String highlight = completed.get(0);

    return new SummaryPayload(
        completed,
        mood,
        moodLabel,
        keywords,
        "今天感觉" + moodLabel + "，已被认真记住。",
        highlight);
  }

  private record SummaryPayload(
      List<String> completed,
      Mood mood,
      String moodLabel,
      List<String> keywords,
      String oneLiner,
      String highlight) {}
}
