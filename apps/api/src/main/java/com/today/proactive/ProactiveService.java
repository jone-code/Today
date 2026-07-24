package com.today.proactive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.common.EntityMapper;
import com.today.common.PromptSource;
import com.today.identity.IdentityService;
import com.today.memory.MemoryDto;
import com.today.memory.MemoryService;
import com.today.persistence.ProactivePromptEventEntity;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProactiveService {

  private static final TypeReference<ProactivePayload> PROACTIVE_TYPE =
      new TypeReference<>() {};

  private final AiGatewayService aiGateway;
  private final CheckinService checkins;
  private final MemoryService memories;
  private final SummaryService summaries;
  private final IdentityService identity;
  private final ProactivePromptEventMapper events;

  public ProactiveService(
      AiGatewayService aiGateway,
      CheckinService checkins,
      MemoryService memories,
      SummaryService summaries,
      IdentityService identity,
      ProactivePromptEventMapper events) {
    this.aiGateway = aiGateway;
    this.checkins = checkins;
    this.memories = memories;
    this.summaries = summaries;
    this.identity = identity;
    this.events = events;
  }

  public ProactiveTodayDto today() {
    String userId = identity.getCurrentUserId();
    String date = checkins.todayDate();
    LocalDate today = LocalDate.parse(date);
    List<CheckinDto> recent = checkins.listRecent(14);
    List<CheckinDto> recentForPrompt =
        recent.size() > 7 ? recent.subList(0, 7) : recent;

    markAnsweredFromHistory(userId, recent);

    List<FollowupCandidate> candidates = buildFollowupCandidates(today, recent);
    Set<String> suppressed = suppressedFingerprints(userId, today);

    String query = buildRetrievalQuery(date, recent);
    List<MemoryDto> retrieved = memories.retrieveRelevant(query, aiGateway.retrieveTopK());

    Map<String, Object> input = new HashMap<>();
    input.put("date", date);
    input.put("recent", recentForPrompt);
    input.put("memories", retrieved);
    input.put("followupCandidates", candidates);
    input.put("suppressFingerprints", List.copyOf(suppressed));

    var result =
        aiGateway.complete(
            AiTask.proactive,
            input,
            PROACTIVE_TYPE,
            () ->
                new ProactivePayload(
                    heuristicPrompts(date, recent, retrieved, candidates, suppressed)));

    List<ProactivePromptDto> prompts =
        result.data() == null || result.data().prompts() == null
            ? List.of()
            : result.data().prompts().stream()
                .filter(p -> p != null && p.text() != null && !p.text().isBlank())
                .map(this::normalizePrompt)
                .toList();

    if (prompts.isEmpty()) {
      prompts = heuristicPrompts(date, recent, retrieved, candidates, suppressed);
    }

    prompts = finalizePrompts(prompts, suppressed, candidates);
    persistShown(userId, today, prompts);

    return new ProactiveTodayDto(date, prompts, result.provider());
  }

  @Transactional
  public ProactivePromptDto select(String promptId) {
    return updateTodayStatus(promptId, PromptEventStatus.selected);
  }

  @Transactional
  public ProactivePromptDto dismiss(String promptId) {
    return updateTodayStatus(promptId, PromptEventStatus.dismissed);
  }

  /** checkin 写入后：用正文覆盖仍开放的追问 */
  @Transactional
  public void markAnsweredFromCheckin(String userId, String checkinDate, String rawText) {
    if (rawText == null || rawText.isBlank()) {
      return;
    }
    Instant now = EntityMapper.now();
    for (ProactivePromptEventEntity open : events.listOpenFollowups(userId)) {
      if (coversFollowup(open, checkinDate, rawText)) {
        events.updateStatus(
            userId, open.getFingerprint(), PromptEventStatus.answered.name(), now);
      }
    }
    // 当天已展示的非 followup：若正文明显回应，也标记 answered
    LocalDate day = LocalDate.parse(checkinDate);
    for (ProactivePromptEventEntity ev : events.listByUserSince(userId, day)) {
      if (!day.equals(ev.getPromptDate())) continue;
      if (!PromptEventStatus.shown.name().equals(ev.getStatus())
          && !PromptEventStatus.selected.name().equals(ev.getStatus())) {
        continue;
      }
      if (PromptSource.gentle.name().equals(ev.getSource())) continue;
      if (textCoversPrompt(rawText, ev.getPromptText())) {
        events.updateStatus(
            userId, ev.getFingerprint(), PromptEventStatus.answered.name(), now);
      }
    }
  }

  private void markAnsweredFromHistory(String userId, List<CheckinDto> recent) {
    Instant now = EntityMapper.now();
    for (ProactivePromptEventEntity open : events.listOpenFollowups(userId)) {
      for (CheckinDto c : recent) {
        if (coversFollowup(open, c.date(), c.rawText())) {
          events.updateStatus(
              userId, open.getFingerprint(), PromptEventStatus.answered.name(), now);
          break;
        }
      }
    }
  }

  private boolean coversFollowup(
      ProactivePromptEventEntity open, String checkinDate, String rawText) {
    if (open.getRelatedDate() != null) {
      LocalDate related = open.getRelatedDate();
      LocalDate checkin = LocalDate.parse(checkinDate);
      // related = 提到安排的那天；作答应在次日及以后
      if (!checkin.isAfter(related)) {
        return false;
      }
    }
    String text = rawText == null ? "" : rawText;
    String prompt = open.getPromptText() == null ? "" : open.getPromptText();
    if (prompt.contains("面试")) {
      return text.matches(".*(面试|通过|挂了|没去|结果|offer).*");
    }
    if (prompt.contains("计划") || prompt.contains("进展") || prompt.contains("安排")) {
      return text.matches(".*(完成|做了|进展|没来得及|推迟|搞定).*");
    }
    return textCoversPrompt(text, prompt);
  }

  private boolean textCoversPrompt(String rawText, String promptText) {
    if (rawText == null || promptText == null) return false;
    // 粗粒度：提示里的关键名词出现在日记中
    if (promptText.contains("累") && rawText.matches(".*(累|休息|睡眠).*")) return true;
    if (promptText.contains("记得") && rawText.length() >= 8) return true;
    return false;
  }

  private Set<String> suppressedFingerprints(String userId, LocalDate today) {
    Set<String> out = new HashSet<>();
    LocalDate since = today.minusDays(14);
    for (ProactivePromptEventEntity ev : events.listByUserSince(userId, since)) {
      String st = ev.getStatus();
      if (PromptEventStatus.answered.name().equals(st)) {
        out.add(ev.getFingerprint());
        continue;
      }
      if (PromptEventStatus.dismissed.name().equals(st)
          && !ev.getPromptDate().isBefore(today.minusDays(7))) {
        out.add(ev.getFingerprint());
        continue;
      }
      // 近 3 天已展示过的 memory/pattern/gentle 不再重复；followup 仍可在到期日出现
      if ((PromptEventStatus.shown.name().equals(st)
              || PromptEventStatus.selected.name().equals(st))
          && !PromptSource.followup.name().equals(ev.getSource())
          && !ev.getPromptDate().isBefore(today.minusDays(3))
          && !ev.getPromptDate().equals(today)) {
        out.add(ev.getFingerprint());
      }
    }
    return out;
  }

  private List<FollowupCandidate> buildFollowupCandidates(
      LocalDate today, List<CheckinDto> recent) {
    Map<String, FollowupCandidate> byFp = new LinkedHashMap<>();
    for (CheckinDto c : recent) {
      LocalDate d = LocalDate.parse(c.date());
      String text = c.rawText() == null ? "" : c.rawText();
      if (text.matches(".*面试.*")) {
        LocalDate due = d.plusDays(1);
        if (!due.isAfter(today) && !due.isBefore(today.minusDays(2))) {
          String fp = PromptFingerprints.ofFollowupTopic("interview", d.toString());
          byFp.putIfAbsent(
              fp,
              new FollowupCandidate(
                  fp,
                  "followup-interview",
                  "之前提到面试，结果怎么样？",
                  d.toString(),
                  "interview"));
        }
      }
      if (text.matches(".*(明天|准备).*") && !text.matches(".*面试.*")) {
        LocalDate due = d.plusDays(1);
        if (due.equals(today)) {
          String fp = PromptFingerprints.ofFollowupTopic("plan", d.toString());
          byFp.putIfAbsent(
              fp,
              new FollowupCandidate(
                  fp,
                  "followup-plan",
                  "你提到过今天的安排，现在进展如何？",
                  d.toString(),
                  "plan"));
        }
      }
    }
    return List.copyOf(byFp.values());
  }

  private String buildRetrievalQuery(String today, List<CheckinDto> recent) {
    StringBuilder sb = new StringBuilder();
    sb.append("今天是 ").append(today).append("。");
    LocalDate todayDate = LocalDate.parse(today);
    String yesterday = todayDate.minusDays(1).toString();
    DaySummaryDto ySummary = findSummaryQuiet(yesterday);
    if (ySummary != null) {
      sb.append("昨日亮点：").append(ySummary.highlight()).append("。");
      sb.append("昨日总结：").append(ySummary.oneLiner()).append("。");
    }
    String recentText =
        recent.stream()
            .limit(3)
            .map(
                c -> {
                  String raw = c.rawText() == null ? "" : c.rawText().trim();
                  if (raw.length() > 120) raw = raw.substring(0, 120) + "…";
                  return c.date() + "：" + raw;
                })
            .collect(Collectors.joining("\n"));
    if (!recentText.isBlank()) {
      sb.append("近期记录：\n").append(recentText);
    } else {
      sb.append("请根据用户长期记忆，温和地开启今日对话。");
    }
    return sb.toString();
  }

  private DaySummaryDto findSummaryQuiet(String date) {
    try {
      return summaries.getByDate(date);
    } catch (Exception e) {
      return null;
    }
  }

  private List<ProactivePromptDto> finalizePrompts(
      List<ProactivePromptDto> raw,
      Set<String> suppressed,
      List<FollowupCandidate> candidates) {
    // 确保到期追问候选优先进入
    List<ProactivePromptDto> merged = new ArrayList<>();
    for (FollowupCandidate c : candidates) {
      if (suppressed.contains(c.fingerprint())) continue;
      merged.add(
          new ProactivePromptDto(
              c.promptId(), c.text(), c.relatedDate(), PromptSource.followup));
    }
    merged.addAll(raw);

    Map<String, ProactivePromptDto> unique = new LinkedHashMap<>();
    for (ProactivePromptDto p : merged) {
      ProactivePromptDto n = normalizePrompt(p);
      String fp = PromptFingerprints.of(n);
      if (suppressed.contains(fp)) continue;
      // followup 候选用稳定 fingerprint
      if (n.source() == PromptSource.followup && n.relatedDate() != null) {
        String topic = n.text().contains("面试") ? "interview" : "plan";
        fp = PromptFingerprints.ofFollowupTopic(topic, n.relatedDate());
        if (suppressed.contains(fp)) continue;
      }
      unique.putIfAbsent(fp, n);
    }

    return unique.values().stream()
        .sorted(
            Comparator.comparingInt((ProactivePromptDto p) -> PromptFingerprints.sourceRank(p.source()))
                .thenComparing(ProactivePromptDto::id))
        .limit(3)
        .toList();
  }

  private void persistShown(String userId, LocalDate today, List<ProactivePromptDto> prompts) {
    Instant now = EntityMapper.now();
    for (ProactivePromptDto p : prompts) {
      String fp =
          p.source() == PromptSource.followup && p.relatedDate() != null
              ? PromptFingerprints.ofFollowupTopic(
                  p.text().contains("面试") ? "interview" : "plan", p.relatedDate())
              : PromptFingerprints.of(p);
      ProactivePromptEventEntity entity = new ProactivePromptEventEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setUserId(userId);
      entity.setPromptDate(today);
      entity.setPromptId(p.id());
      entity.setFingerprint(fp);
      entity.setSource(p.source().name());
      entity.setPromptText(p.text().length() > 512 ? p.text().substring(0, 512) : p.text());
      entity.setRelatedDate(
          p.relatedDate() == null || p.relatedDate().isBlank()
              ? null
              : LocalDate.parse(p.relatedDate()));
      entity.setStatus(PromptEventStatus.shown.name());
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      events.upsert(entity);
    }
  }

  private ProactivePromptDto updateTodayStatus(String promptId, PromptEventStatus status) {
    String userId = identity.getCurrentUserId();
    LocalDate today = LocalDate.parse(checkins.todayDate());
    Instant now = EntityMapper.now();
    int updated =
        events.updateStatusByPromptIdAndDate(userId, promptId, today, status.name(), now);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "prompt not found for today");
    }
    for (ProactivePromptEventEntity ev : events.listByUserSince(userId, today)) {
      if (today.equals(ev.getPromptDate()) && promptId.equals(ev.getPromptId())) {
        return new ProactivePromptDto(
            ev.getPromptId(),
            ev.getPromptText(),
            ev.getRelatedDate() == null ? null : ev.getRelatedDate().toString(),
            PromptSource.valueOf(ev.getSource()));
      }
    }
    return new ProactivePromptDto(promptId, "", null, PromptSource.gentle);
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
      String today,
      List<CheckinDto> recent,
      List<MemoryDto> memoryList,
      List<FollowupCandidate> candidates,
      Set<String> suppressed) {
    List<ProactivePromptDto> prompts = new ArrayList<>();

    for (FollowupCandidate c : candidates) {
      if (suppressed.contains(c.fingerprint())) continue;
      prompts.add(
          new ProactivePromptDto(
              c.promptId(), c.text(), c.relatedDate(), PromptSource.followup));
    }

    long tiredCount =
        recent.stream().filter(e -> e.rawText().matches(".*(很累|疲惫|累).*")).count();
    if (tiredCount >= 3) {
      ProactivePromptDto tired =
          new ProactivePromptDto(
              "pattern-tired",
              "最近两周你已经有 " + tiredCount + " 天提到“累”，我在陪你留意这件事。",
              null,
              PromptSource.pattern);
      if (!suppressed.contains(PromptFingerprints.of(tired))) {
        prompts.add(tired);
      }
    }

    if (!memoryList.isEmpty() && prompts.size() < 2) {
      MemoryDto top = memoryList.get(0);
      ProactivePromptDto mem =
          new ProactivePromptDto(
              "memory-" + Integer.toHexString(top.id().hashCode()),
              "我还记得：" + top.text() + "。今天有没有新的变化？",
              null,
              PromptSource.memory);
      if (!suppressed.contains(PromptFingerprints.of(mem))) {
        prompts.add(mem);
      }
    }

    if (prompts.isEmpty()) {
      prompts.add(
          new ProactivePromptDto(
              "gentle-checkin", "我在这里。用几句话留下今天就好。", null, PromptSource.gentle));
    }

    return prompts.stream().limit(3).toList();
  }

  public record ProactivePayload(List<ProactivePromptDto> prompts) {}

  public record FollowupCandidate(
      String fingerprint, String promptId, String text, String relatedDate, String topic) {}
}
