package com.today.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.aigateway.VectorMath;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.persistence.MemoryEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryService {

  private static final TypeReference<MemoryExtractPayload> EXTRACT_TYPE =
      new TypeReference<>() {};

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

  /**
   * 按 query 检索最相关记忆：有 embedding 走余弦相似度，否则按 strength 降级。
   */
  public List<MemoryDto> retrieveRelevant(String queryText, int topK) {
    String userId = identity.getCurrentUserId();
    List<MemoryEntity> all = memoryMapper.listByUserId(userId);
    if (all.isEmpty()) {
      return List.of();
    }
    int limit = Math.max(1, topK);

    Optional<List<float[]>> queryEmbed =
        aiGateway.embed(List.of(queryText == null ? "" : queryText));
    if (queryEmbed.isPresent()) {
      float[] query = queryEmbed.get().get(0);
      record Scored(MemoryEntity entity, double score) {}
      List<Scored> scored = new ArrayList<>();
      for (MemoryEntity entity : all) {
        float[] vec = JsonUtils.fromJsonFloatArray(entity.getEmbeddingJson());
        if (vec == null) {
          continue;
        }
        scored.add(new Scored(entity, VectorMath.cosineSimilarity(query, vec)));
      }
      if (!scored.isEmpty()) {
        return scored.stream()
            .sorted(Comparator.comparingDouble(Scored::score).reversed())
            .limit(limit)
            .map(s -> EntityMapper.toDto(s.entity()))
            .toList();
      }
    }

    return all.stream().limit(limit).map(EntityMapper::toDto).toList();
  }

  @Transactional
  public List<MemoryDto> upsertFromCheckin(String rawText) {
    return upsertFromCheckin(identity.getCurrentUserId(), rawText);
  }

  @Transactional
  public List<MemoryDto> upsertFromCheckin(String userId, String rawText) {
    var result =
        aiGateway.complete(
            AiTask.memory_extract,
            Map.of("rawText", rawText),
            EXTRACT_TYPE,
            () -> new MemoryExtractPayload(heuristicExtract(rawText)));

    Instant now = EntityMapper.now();
    List<MemoryCandidate> items =
        result.data() == null || result.data().items() == null
            ? List.of()
            : result.data().items().stream()
                .filter(i -> i != null && i.text() != null && !i.text().isBlank())
                .filter(i -> i.category() != null)
                .limit(5)
                .toList();

    List<MemoryEntity> needEmbed = new ArrayList<>();

    for (MemoryCandidate item : items) {
      String text = item.text().trim();
      String id = userId + ":" + item.category().name() + ":" + text;
      MemoryEntity existing = memoryMapper.findById(id);
      if (existing == null) {
        MemoryEntity created = new MemoryEntity();
        created.setId(id);
        created.setUserId(userId);
        created.setCategory(item.category().name());
        created.setMemoryText(text);
        created.setStrength(1);
        created.setUpdatedAt(now);
        memoryMapper.insert(created);
        needEmbed.add(created);
      } else {
        existing.setStrength(existing.getStrength() + 1);
        existing.setUpdatedAt(now);
        if (existing.getEmbeddingJson() == null || existing.getEmbeddingJson().isBlank()) {
          needEmbed.add(existing);
        } else {
          memoryMapper.update(existing);
        }
      }
    }

    backfillEmbeddings(needEmbed, now);
    return memoryMapper.listByUserId(userId).stream().map(EntityMapper::toDto).toList();
  }

  private void backfillEmbeddings(List<MemoryEntity> entities, Instant now) {
    if (entities.isEmpty()) {
      return;
    }
    List<String> texts = entities.stream().map(MemoryEntity::getMemoryText).toList();
    Optional<List<float[]>> vectors = aiGateway.embed(texts);
    if (vectors.isEmpty()) {
      for (MemoryEntity entity : entities) {
        memoryMapper.update(entity);
      }
      return;
    }
    List<float[]> vecs = vectors.get();
    for (int i = 0; i < entities.size(); i++) {
      MemoryEntity entity = entities.get(i);
      entity.setEmbeddingJson(JsonUtils.toJsonFloatArray(vecs.get(i)));
      entity.setUpdatedAt(now);
      memoryMapper.update(entity);
    }
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

  public record MemoryExtractPayload(List<MemoryCandidate> items) {}

  public record MemoryCandidate(MemoryCategory category, String text) {}
}
