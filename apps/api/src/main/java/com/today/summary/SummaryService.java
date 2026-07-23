package com.today.summary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.common.Mood;
import com.today.identity.IdentityService;
import com.today.persistence.DaySummaryEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SummaryService {

  private static final Pattern SPLIT = Pattern.compile("[。！？!?\\n；;]+");
  private static final List<String> KEYWORD_CANDIDATES =
      List.of("工作", "学习", "家庭", "运动", "创业", "面试");
  private static final TypeReference<SummaryPayload> SUMMARY_TYPE =
      new TypeReference<>() {};

  private final SummaryMapper summaryMapper;
  private final AiGatewayService aiGateway;
  private final IdentityService identity;

  public SummaryService(
      SummaryMapper summaryMapper, AiGatewayService aiGateway, IdentityService identity) {
    this.summaryMapper = summaryMapper;
    this.aiGateway = aiGateway;
    this.identity = identity;
  }

  @Transactional
  public DaySummaryDto generateForCheckin(String checkinId, String date, String rawText) {
    var result =
        aiGateway.complete(
            AiTask.summary,
            Map.of("rawText", rawText),
            SUMMARY_TYPE,
            () -> heuristicSummary(rawText));

    SummaryPayload payload = normalize(result.data());
    Instant now = EntityMapper.now();
    LocalDate summaryDate = EntityMapper.parseDate(date);
    String userId = identity.getCurrentUserId();

    DaySummaryEntity entity = new DaySummaryEntity();
    entity.setCheckinId(checkinId);
    entity.setUserId(userId);
    entity.setSummaryDate(summaryDate);
    entity.setCompletedJson(JsonUtils.toJson(payload.completed()));
    entity.setMood(payload.mood().name());
    entity.setMoodLabel(payload.moodLabel());
    entity.setKeywordsJson(JsonUtils.toJson(payload.keywords()));
    entity.setOneLiner(payload.oneLiner());
    entity.setHighlight(payload.highlight());
    entity.setProvider(result.provider().name());
    entity.setCreatedAt(now);

    summaryMapper.upsert(entity);
    return EntityMapper.toDto(entity);
  }

  public DaySummaryDto getByDate(String date) {
    DaySummaryEntity found =
        summaryMapper.findByUserIdAndDate(
            identity.getCurrentUserId(), EntityMapper.parseDate(date));
    if (found == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "summary not found: " + date);
    }
    return EntityMapper.toDto(found);
  }

  private SummaryPayload normalize(SummaryPayload raw) {
    List<String> completed =
        raw.completed() == null || raw.completed().isEmpty()
            ? List.of("记录了今天的片刻")
            : raw.completed().stream().map(String::trim).filter(s -> !s.isEmpty()).limit(3).toList();
    Mood mood = raw.mood() == null ? Mood.okay : raw.mood();
    String moodLabel =
        raw.moodLabel() == null || raw.moodLabel().isBlank()
            ? defaultMoodLabel(mood)
            : raw.moodLabel().trim();
    List<String> keywords =
        raw.keywords() == null || raw.keywords().isEmpty()
            ? List.of("日常")
            : raw.keywords().stream().map(String::trim).filter(s -> !s.isEmpty()).limit(4).toList();
    String oneLiner =
        raw.oneLiner() == null || raw.oneLiner().isBlank()
            ? "今天感觉" + moodLabel + "，已被认真记住。"
            : raw.oneLiner().trim();
    String highlight =
        raw.highlight() == null || raw.highlight().isBlank() ? completed.get(0) : raw.highlight().trim();
    return new SummaryPayload(completed, mood, moodLabel, keywords, oneLiner, highlight);
  }

  private static String defaultMoodLabel(Mood mood) {
    return switch (mood) {
      case great -> "很好";
      case good -> "不错";
      case okay -> "平常";
      case tired -> "疲惫";
      case low -> "低落";
    };
  }

  private SummaryPayload heuristicSummary(String rawText) {
    String text = rawText.trim();
    Mood mood =
        text.matches(".*(累|疲惫|加班).*")
            ? Mood.tired
            : text.matches(".*(开心|顺利|收获).*") ? Mood.great : Mood.okay;
    String moodLabel = defaultMoodLabel(mood);

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

    List<String> completed =
        parts.isEmpty() ? List.of("记录了今天的片刻") : parts.subList(0, Math.min(2, parts.size()));
    String highlight = completed.get(0);

    return new SummaryPayload(
        completed,
        mood,
        moodLabel,
        keywords,
        "今天感觉" + moodLabel + "，已被认真记住。",
        highlight);
  }

  /** LLM / Heuristic 共用的结构化总结载荷 */
  public record SummaryPayload(
      List<String> completed,
      Mood mood,
      String moodLabel,
      List<String> keywords,
      String oneLiner,
      String highlight) {}
}
