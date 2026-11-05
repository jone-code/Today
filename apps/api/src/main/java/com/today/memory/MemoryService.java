package com.today.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.EntityMapper;
import com.today.common.JsonUtils;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.persistence.MemoryEntity;
import com.today.persistence.MemorySourceEntity;
import com.today.vector.ScoredMemoryId;
import com.today.vector.VectorStore;
import com.today.vector.VectorSyncJobMapper;
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
  private final MemorySourceMapper sourceMapper;
  private final VectorSyncJobMapper vectorJobs;

  public MemoryService(
      MemoryMapper memoryMapper,
      AiGatewayService aiGateway,
      IdentityService identity,
      VectorStore vectorStore,
      MemorySourceMapper sourceMapper,
      VectorSyncJobMapper vectorJobs) {
    this.memoryMapper = memoryMapper;
    this.aiGateway = aiGateway;
    this.identity = identity;
    this.vectorStore = vectorStore;
    this.sourceMapper = sourceMapper;
    this.vectorJobs = vectorJobs;
  }

  public List<MemoryDto> list(boolean includeArchived) {
    return memoryMapper.listByUserId(identity.getCurrentUserId(), includeArchived).stream()
        .map(EntityMapper::toDto)
        .toList();
  }

  public List<MemoryDto> list() {
    return list(false);
  }

  public List<MemoryOriginDto> origins(String id) {
    MemoryEntity memory = requireOwned(id);
    return sourceMapper.listDisplayableOrigins(memory.getId(), memory.getUserId()).stream()
        .map(origin -> new MemoryOriginDto(
            "checkin".equals(origin.getSourceType()) ? "checkin" : "conversation",
            origin.getSourceId(),
            origin.getCheckinDate() == null ? null : origin.getCheckinDate().toString(),
            origin.getConversationId()))
        .toList();
  }

  /** Legacy memories without a live, confirmed source are not injected into conversation prompts. */
  public List<Map<String, String>> confirmedForConversation(int limit) {
    return memoryMapper.listConfirmedForAgent(identity.getCurrentUserId(), Math.min(10, Math.max(1, limit)))
        .stream()
        .map(memory -> Map.of("category", memory.getCategory(), "text", memory.getMemoryText()))
        .toList();
  }

  /** 按 query 检索最相关记忆（默认排除已归档） */
  public List<MemoryDto> retrieveRelevant(String queryText, int topK) {
    String userId = identity.getCurrentUserId();
    int limit = Math.max(1, topK);
    List<MemoryEntity> confirmed = memoryMapper.listConfirmedForAgent(userId, limit);
    if (confirmed.isEmpty()) {
      return List.of();
    }

    Optional<List<float[]>> queryEmbed =
        aiGateway.embed(List.of(queryText == null ? "" : queryText));
    if (queryEmbed.isPresent()) {
      float[] query = queryEmbed.get().get(0);
      List<ScoredMemoryId> hits = vectorStore.search(userId, query, limit);
      if (hits != null && !hits.isEmpty()) {
        List<MemoryDto> out = new ArrayList<>();
        for (ScoredMemoryId hit : hits) {
          MemoryEntity entity = memoryMapper.findConfirmedById(hit.memoryId(), userId);
          if (entity == null) {
            continue;
          }
          out.add(EntityMapper.toDto(entity));
        }
        if (!out.isEmpty()) {
          return out;
        }
      }
    }

    return confirmed.stream().map(EntityMapper::toDto).toList();
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
    }
    enqueueVectorSync(entity);
    return EntityMapper.toDto(requireOwned(id));
  }

  @Transactional
  public MemoryDto archive(String id, boolean archived) {
    MemoryEntity entity = requireOwned(id);
    entity.setArchived(archived);
    entity.setUpdatedAt(EntityMapper.now());
    memoryMapper.update(entity);
    enqueueVectorSync(entity);
    return EntityMapper.toDto(entity);
  }

  @Transactional
  public void delete(String id) {
    MemoryEntity entity = requireOwned(id);
    memoryMapper.deleteById(id, identity.getCurrentUserId());
    enqueueVectorSync(entity);
  }

  /** Extracts suggestions only. A checkin must never write long-term memory without confirmation. */
  public List<MemoryCandidate> extractCandidatesFromCheckin(String rawText) {
    var result =
        aiGateway.complete(
            AiTask.memory_extract,
            Map.of("rawText", rawText),
            EXTRACT_TYPE,
            () -> new MemoryExtractPayload(heuristicExtract(rawText)));

    return result.data() == null || result.data().items() == null
            ? List.of()
            : result.data().items().stream()
                .filter(i -> i != null && i.text() != null && !i.text().isBlank())
                .filter(i -> i.category() != null)
                .limit(5)
                .toList();
  }

  @Transactional
  public MemoryDto confirmFromProposal(
      String userId, MemoryCategory category, String text, String sourceType, String sourceId) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank() || normalized.length() > 512) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory text is invalid");
    }
    // A conversation-derived memory has its own identity; legacy memories lack source records.
    String id = java.util.UUID.randomUUID().toString();
    Instant now = EntityMapper.now();
    MemoryEntity entity = new MemoryEntity();
    entity.setId(id);
    entity.setUserId(userId);
    entity.setCategory(category.name());
    entity.setMemoryText(normalized);
    entity.setStrength(1);
    entity.setArchived(false);
    entity.setUpdatedAt(now);
    memoryMapper.insert(entity);
    MemorySourceEntity source = new MemorySourceEntity();
    source.setMemoryId(entity.getId());
    source.setUserId(userId);
    source.setSourceType(sourceType);
    source.setSourceId(sourceId);
    source.setActive(true);
    source.setCreatedAt(now);
    sourceMapper.insert(source);
    backfillEmbeddings(List.of(entity), now);
    enqueueVectorSync(entity);
    return EntityMapper.toDto(memoryMapper.findById(entity.getId()));
  }

  @Transactional
  public void deactivateConversationSource(String userId, String messageId) {
    deactivateSource(userId, "conversation_message", messageId);
  }

  @Transactional
  public void deactivateCheckinSource(String userId, String checkinId) {
    deactivateSource(userId, "checkin", checkinId);
  }

  private void deactivateSource(String userId, String sourceType, String sourceId) {
    List<MemorySourceEntity> affected = sourceMapper.listBySource(userId, sourceType, sourceId);
    sourceMapper.deactivateBySource(userId, sourceType, sourceId);
    for (MemorySourceEntity source : affected) {
      if (!sourceMapper.listByMemory(source.getMemoryId(), userId).isEmpty()) continue;
      MemoryEntity memory = memoryMapper.findById(source.getMemoryId());
      if (memory != null && userId.equals(memory.getUserId())) {
        memory.setArchived(true);
        memory.setUpdatedAt(EntityMapper.now());
        memoryMapper.update(memory);
        enqueueVectorSync(memory);
      }
    }
  }

  private MemoryEntity requireOwned(String id) {
    MemoryEntity entity = memoryMapper.findVisibleById(id, identity.getCurrentUserId());
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
    for (int i = 0; i < entities.size(); i++) {
      MemoryEntity entity = entities.get(i);
      entity.setEmbeddingJson(JsonUtils.toJsonFloatArray(vecs.get(i)));
      entity.setUpdatedAt(now);
      memoryMapper.update(entity);
    }
  }

  private void enqueueVectorSync(MemoryEntity entity) {
    vectorJobs.enqueue(entity.getId(), entity.getUserId(), EntityMapper.now());
  }

  private List<MemoryCandidate> heuristicExtract(String rawText) {
    List<MemoryCandidate> out = new ArrayList<>();
    if (rawText.matches("(?s).*(每天|每周|长期|一直|坚持)[^。！？，,；;\\n]{0,20}(运动|跑步|健身).*")) {
      out.add(new MemoryCandidate(MemoryCategory.health, "正在坚持运动"));
    }
    if (rawText.matches("(?s).*(每天|每周|长期|一直|持续|坚持)[^。！？，,；;\\n]{0,20}(学习|课程|学AI).*")) {
      out.add(new MemoryCandidate(MemoryCategory.learning, "正在持续学习"));
    }
    if (rawText.matches("(?s).*(正在|计划|准备|打算)[^。！？，,；;\\n]{0,20}(创业|做产品|开发产品).*")) {
      out.add(new MemoryCandidate(MemoryCategory.goal, "有创业或产品目标"));
    }
    return out;
  }

  public record MemoryExtractPayload(List<MemoryCandidate> items) {}

  public record MemoryCandidate(MemoryCategory category, String text) {}
}
