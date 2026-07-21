package com.today.memory;

import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.EntityMapper;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryService {

  private final MemoryMapper memoryMapper;
  private final AiGatewayService aiGateway;
  private final IdentityService identity;

  public MemoryService(
      MemoryMapper memoryMapper, AiGatewayService aiGateway, IdentityService identity) {
    this.memoryMapper = memoryMapper;
    this.aiGateway = aiGateway;
    this.identity = identity;
  }

  public List<MemoryDto> list() {
    return memoryMapper.listByUserId(identity.getCurrentUserId()).stream()
        .map(EntityMapper::toDto)
        .toList();
  }

  @Transactional
  public List<MemoryDto> upsertFromCheckin(String rawText) {
    var result =
        aiGateway.complete(
            AiTask.memory_extract,
            Map.of("rawText", rawText),
            () -> heuristicExtract(rawText));

    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();

    for (MemoryCandidate item : result.data()) {
      String id = userId + ":" + item.category().name() + ":" + item.text();
      MemoryEntity existing = memoryMapper.findById(id);
      if (existing == null) {
        MemoryEntity created = new MemoryEntity();
        created.setId(id);
        created.setUserId(userId);
        created.setCategory(item.category().name());
        created.setMemoryText(item.text());
        created.setStrength(1);
        created.setUpdatedAt(now);
        memoryMapper.insert(created);
      } else {
        existing.setStrength(existing.getStrength() + 1);
        existing.setUpdatedAt(now);
        memoryMapper.update(existing);
      }
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
