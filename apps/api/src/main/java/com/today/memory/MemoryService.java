package com.today.memory;

import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

  private final Map<String, MemoryDto> store = new ConcurrentHashMap<>();
  private final AiGatewayService aiGateway;
  private final IdentityService identity;

  public MemoryService(AiGatewayService aiGateway, IdentityService identity) {
    this.aiGateway = aiGateway;
    this.identity = identity;
  }

  public List<MemoryDto> list() {
    String userId = identity.getCurrentUserId();
    return store.values().stream()
        .filter(m -> m.userId().equals(userId))
        .sorted(
            Comparator.comparing(MemoryDto::strength)
                .reversed()
                .thenComparing(Comparator.comparing(MemoryDto::updatedAt).reversed()))
        .toList();
  }

  public List<MemoryDto> upsertFromCheckin(String rawText) {
    var result =
        aiGateway.complete(
            AiTask.memory_extract,
            Map.of("rawText", rawText),
            () -> heuristicExtract(rawText));

    String userId = identity.getCurrentUserId();
    String now = Instant.now().toString();

    for (MemoryCandidate item : result.data()) {
      String id = userId + ":" + item.category() + ":" + item.text();
      MemoryDto existing = store.get(id);
      store.put(
          id,
          new MemoryDto(
              id,
              userId,
              item.category(),
              item.text(),
              existing != null ? existing.strength() + 1 : 1,
              now));
    }

    return list();
  }

  private List<MemoryCandidate> heuristicExtract(String rawText) {
    List<MemoryCandidate> out = new ArrayList<>();
    if (rawText.matches(".*(压力|加班|上线).*")) {
      out.add(new MemoryCandidate(MemoryCategory.work, "最近工作压力偏高"));
    }
    if (rawText.matches(".*(运动|跑步|健身).*")) {
      out.add(new MemoryCandidate(MemoryCategory.health, "正在坚持运动"));
    }
    if (rawText.matches(".*(学习|AI|课程).*")) {
      out.add(new MemoryCandidate(MemoryCategory.learning, "正在持续学习"));
    }
    if (rawText.matches(".*(创业|产品|Demo|MVP).*")) {
      out.add(new MemoryCandidate(MemoryCategory.goal, "正在推进创业 / 产品"));
    }
    if (rawText.matches(".*面试.*")) {
      out.add(new MemoryCandidate(MemoryCategory.goal, "近期有面试相关安排"));
    }
    if (rawText.matches(".*(累|疲惫).*")) {
      out.add(new MemoryCandidate(MemoryCategory.emotion, "近期多次提到疲惫"));
    }
    return out;
  }

  private record MemoryCandidate(MemoryCategory category, String text) {}
}
