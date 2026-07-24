package com.today.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.persistence.MemoryEntity;
import com.today.vector.MemoryVectorRecord;
import com.today.vector.ScoredMemoryId;
import com.today.vector.VectorStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemoryService {

  private static final TypeReference<MemoryExtractPayload> EXTRACT_TYPE =
      new TypeReference<>() {};

  private final MemoryMapper memoryMapper;
  private final AiGatewayService aiGateway;
  private final IdentityService identity;
  private final VectorStore vectorStore;

  public MemoryService(
      MemoryMapper memoryMapper,
      AiGatewayService aiGateway,
      IdentityService identity,
      VectorStore vectorStore) {
    this.memoryMapper = memoryMapper;
    this.aiGateway = aiGateway;
    this.identity = identity;
    this.vectorStore = vectorStore;
  }

  public List<MemoryDto> list(boolean includeArchived) {
    return memoryMapper.listByUserId(identity.getCurrentUserId(), includeArchived).stream()
        .map(EntityMapper::toDto)
        .toList();
  }

  public List<MemoryDto> list() {
    return list(false);
  }

  /** 按 query 检索最相关记忆（默认排除已归档） */
  public List<MemoryDto> retrieveRelevant(String queryText, int topK) {
    String userId = identity.getCurrentUserId();
    List<MemoryEntity> all = memoryMapper.listByUserId(userId, false);
    if (all.isEmpty()) {
      return List.of();
    }
    int limit = Math.max(1, topK);

    Optional<List<float[]>> queryEmbed =
        aiGateway.embed(List.of(queryText == null ? "" : queryText));
    if (queryEmbed.isPresent()) {
      float[] query = queryEmbed.get().get(0);
      List<ScoredMemoryId> hits = vectorStore.search(userId, query, limit);
      if (hits != null && !hits.isEmpty()) {
        List<MemoryDto> out = new ArrayList<>();
        for (ScoredMemoryId hit : hits) {
          MemoryEntity entity = memoryMapper.findById(hit.memoryId());
          if (entity == null || entity.isArchived() || !userId.equals(entity.getUserId())) {
            continue;
          }
          out.add(EntityMapper.toDto(entity));
        }
        if (!out.isEmpty()) {
          return out;
        }
      }
    }

    return all.stream().limit(limit).map(EntityMapper::toDto).toList();
  }

  @Transactional
  public MemoryDto update(String id, MemoryUpdateRequest body) {
    MemoryEntity entity = requireOwned(id);
    boolean textChanged = false;
    if (body.getText() != null) {
      String text = body.getText().trim();
      if (text.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory text is blank");
      }
      if (text.length() > 512) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory text too long");
      }
      textChanged = !text.equals(entity.getMemoryText());
      entity.setMemoryText(text);
    }
    if (body.getCategory() != null) {
      entity.setCategory(body.getCategory().name());
    }
    entity.setUpdatedAt(EntityMapper.now());
    if (textChanged) {
      entity.setEmbeddingJson(null);
      memoryMapper.update(entity);
      backfillEmbeddings(List.of(entity), entity.getUpdatedAt());
    } else {
      memoryMapper.update(entity);
      syncVectorIndex(entity);
    }
    return EntityMapper.toDto(requireOwned(id));
  }

  @Transactional
  public MemoryDto archive(String id, boolean archived) {
    MemoryEntity entity = requireOwned(id);
    entity.setArchived(archived);
    entity.setUpdatedAt(EntityMapper.now());
    memoryMapper.update(entity);
    vectorStore.setArchived(entity.getId(), entity.getUserId(), archived);
    return EntityMapper.toDto(entity);
  }

  @Transactional
  public void delete(String id) {
    MemoryEntity entity = requireOwned(id);
    memoryMapper.deleteById(id, identity.getCurrentUserId());
    vectorStore.delete(entity.getId());
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
        created.setArchived(false);
        created.setUpdatedAt(now);
        memoryMapper.insert(created);
        needEmbed.add(created);
      } else {
        existing.setStrength(existing.getStrength() + 1);
        existing.setArchived(false);
        existing.setUpdatedAt(now);
        if (existing.getEmbeddingJson() == null || existing.getEmbeddingJson().isBlank()) {
          needEmbed.add(existing);
        } else {
          memoryMapper.update(existing);
          syncVectorIndex(existing);
        }
      }
    }

    backfillEmbeddings(needEmbed, now);
    return memoryMapper.listByUserId(userId, false).stream().map(EntityMapper::toDto).toList();
  }

  private MemoryEntity requireOwned(String id) {
    MemoryEntity entity = memoryMapper.findById(id);
    if (entity == null || !identity.getCurrentUserId().equals(entity.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "memory not found");
    }
    return entity;
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
    List<MemoryVectorRecord> records = new ArrayList<>();
    for (int i = 0; i < entities.size(); i++) {
      MemoryEntity entity = entities.get(i);
      entity.setEmbeddingJson(JsonUtils.toJsonFloatArray(vecs.get(i)));
      entity.setUpdatedAt(now);
      memoryMapper.update(entity);
      records.add(toRecord(entity, vecs.get(i)));
    }
    vectorStore.upsertAll(records);
  }

  private void syncVectorIndex(MemoryEntity entity) {
    float[] vec = JsonUtils.fromJsonFloatArray(entity.getEmbeddingJson());
    if (vec == null) {
      vectorStore.setArchived(entity.getId(), entity.getUserId(), entity.isArchived());
      return;
    }
    vectorStore.upsert(toRecord(entity, vec));
  }

  private static MemoryVectorRecord toRecord(MemoryEntity entity, float[] vector) {
    return new MemoryVectorRecord(
        entity.getId(),
        entity.getUserId(),
        entity.getCategory(),
        entity.getMemoryText(),
        entity.getStrength(),
        entity.isArchived(),
        vector);
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
