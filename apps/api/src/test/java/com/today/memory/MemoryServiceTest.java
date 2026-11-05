package com.today.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiResult;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.common.AiProvider;
import com.today.common.MemoryCategory;
import com.today.identity.IdentityService;
import com.today.persistence.MemoryEntity;
import com.today.persistence.MemorySourceEntity;
import com.today.vector.VectorStore;
import com.today.vector.ScoredMemoryId;
import com.today.vector.VectorSyncJobMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MemoryServiceTest {

  @Test
  void sourcesAreScopedToVisibleMemoryAndContainNoOriginalText() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    MemorySourceMapper sources = mock(MemorySourceMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    MemoryEntity memory = new MemoryEntity();
    memory.setId("memory-1"); memory.setUserId("user-1");
    when(mapper.findVisibleById("memory-1", "user-1")).thenReturn(memory);
    MemoryOrigin origin = new MemoryOrigin();
    origin.setSourceType("checkin"); origin.setSourceId("checkin-1");
    origin.setCheckinDate(LocalDate.of(2026, 11, 3));
    when(sources.listDisplayableOrigins("memory-1", "user-1")).thenReturn(List.of(origin));
    var service = new MemoryService(mapper, mock(AiGatewayService.class), identity,
        mock(VectorStore.class), sources, mock(VectorSyncJobMapper.class));

    assertEquals(List.of(new MemoryOriginDto("checkin", "checkin-1", "2026-11-03", null)),
        service.origins("memory-1"));
    assertEquals(404, org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.origins("other-memory")).getStatusCode().value());
    verify(sources).listDisplayableOrigins("memory-1", "user-1");
  }

  @Test
  void conversationOnlyReceivesLimitedConfirmedMemoriesForCurrentUser() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    IdentityService identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    MemoryEntity memory = new MemoryEntity();
    memory.setCategory("life");
    memory.setMemoryText("正在筹备旅行");
    when(mapper.listConfirmedForAgent("user-1", 10)).thenReturn(List.of(memory));

    var service = new MemoryService(mapper, mock(AiGatewayService.class), identity,
        mock(VectorStore.class), mock(MemorySourceMapper.class), mock(VectorSyncJobMapper.class));
    assertEquals(List.of(Map.of("category", "life", "text", "正在筹备旅行")),
        service.confirmedForConversation(100));
  }

  @Test
  void checkinExtractionDoesNotPersistMemory() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    MemorySourceMapper sources = mock(MemorySourceMapper.class);
    VectorStore vectors = mock(VectorStore.class);
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService gateway = mock(AiGatewayService.class);
    var suggestion = new MemoryService.MemoryCandidate(MemoryCategory.health, "正在坚持运动");
    when(gateway.complete(eq(AiTask.memory_extract), any(), any(TypeReference.class), any(Supplier.class)))
        .thenReturn(new AiResult<>(new MemoryService.MemoryExtractPayload(List.of(suggestion)), AiProvider.llm));

    var service = new MemoryService(mapper, gateway, identity, vectors, sources,
        mock(VectorSyncJobMapper.class));
    assertEquals(List.of(suggestion), service.extractCandidatesFromCheckin("今天跑步了"));
    verifyNoInteractions(mapper, sources, vectors, identity);
  }

  @Test
  void fallbackDoesNotTurnOneDayEventsOrEmotionsIntoLongTermMemories() {
    AiGatewayService gateway = mock(AiGatewayService.class);
    when(gateway.complete(eq(AiTask.memory_extract), any(), any(TypeReference.class), any(Supplier.class)))
        .thenAnswer(invocation -> new AiResult<>(
            ((Supplier<?>) invocation.getArgument(3)).get(), AiProvider.heuristic));
    var service = new MemoryService(mock(MemoryMapper.class), gateway, mock(IdentityService.class),
        mock(VectorStore.class), mock(MemorySourceMapper.class), mock(VectorSyncJobMapper.class));

    assertEquals(List.of(), service.extractCandidatesFromCheckin("今天很累，跑了一次步，还学了点 AI，工作压力有点大。"));
    assertEquals(List.of(), service.extractCandidatesFromCheckin("我每天压力大，今天只跑了一次步。"));
    assertEquals(List.of(
        new MemoryService.MemoryCandidate(MemoryCategory.health, "正在坚持运动"),
        new MemoryService.MemoryCandidate(MemoryCategory.learning, "正在持续学习"),
        new MemoryService.MemoryCandidate(MemoryCategory.goal, "有创业或产品目标")),
        service.extractCandidatesFromCheckin("我每周坚持跑步。我每天学习英语。我计划做产品。"));
  }

  @Test
  void retrievalNeverInjectsUnconfirmedVectorHits() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService gateway = mock(AiGatewayService.class);
    VectorStore vectors = mock(VectorStore.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    MemoryEntity confirmed = new MemoryEntity();
    confirmed.setId("confirmed");
    confirmed.setUserId("user-1");
    confirmed.setCategory("life");
    confirmed.setMemoryText("已同意保存");
    confirmed.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(mapper.listConfirmedForAgent("user-1", 2)).thenReturn(List.of(confirmed));
    when(gateway.embed(List.of("query"))).thenReturn(Optional.of(List.of(new float[] { 1f })));
    when(vectors.search(eq("user-1"), any(float[].class), eq(2)))
        .thenReturn(List.of(new ScoredMemoryId("legacy", 0.9), new ScoredMemoryId("confirmed", 0.8)));
    when(mapper.findConfirmedById("confirmed", "user-1")).thenReturn(confirmed);

    var service = new MemoryService(mapper, gateway, identity, vectors,
        mock(MemorySourceMapper.class), mock(VectorSyncJobMapper.class));
    assertEquals(List.of("confirmed"), service.retrieveRelevant("query", 2).stream().map(MemoryDto::id).toList());
    verify(mapper).findConfirmedById("legacy", "user-1");
  }

  @Test
  void retrievalWithoutEmbeddingsUsesConfirmedFallbackOnly() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    IdentityService identity = mock(IdentityService.class);
    AiGatewayService gateway = mock(AiGatewayService.class);
    VectorStore vectors = mock(VectorStore.class);
    when(identity.getCurrentUserId()).thenReturn("user-1");
    MemoryEntity confirmed = new MemoryEntity();
    confirmed.setId("confirmed");
    confirmed.setCategory("life");
    confirmed.setMemoryText("已同意保存");
    confirmed.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(mapper.listConfirmedForAgent("user-1", 1)).thenReturn(List.of(confirmed));
    when(gateway.embed(List.of("query"))).thenReturn(Optional.empty());

    var service = new MemoryService(mapper, gateway, identity, vectors,
        mock(MemorySourceMapper.class), mock(VectorSyncJobMapper.class));
    assertEquals(List.of("confirmed"), service.retrieveRelevant("query", 1).stream().map(MemoryDto::id).toList());
    verifyNoInteractions(vectors);
  }

  @Test
  void changingCheckinArchivesSolelyDerivedMemoryAndQueuesVectorRemoval() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    MemorySourceMapper sources = mock(MemorySourceMapper.class);
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemorySourceEntity source = new MemorySourceEntity();
    source.setMemoryId("memory-1");
    MemoryEntity memory = new MemoryEntity();
    memory.setId("memory-1"); memory.setUserId("user-1");
    when(sources.listBySource("user-1", "checkin", "checkin-1")).thenReturn(List.of(source));
    when(mapper.findById("memory-1")).thenReturn(memory);
    var service = new MemoryService(mapper, mock(AiGatewayService.class), mock(IdentityService.class),
        mock(VectorStore.class), sources, jobs);

    service.deactivateCheckinSource("user-1", "checkin-1");

    verify(sources).deactivateBySource("user-1", "checkin", "checkin-1");
    assertEquals(true, memory.isArchived());
    verify(mapper).update(memory);
    verify(jobs).enqueue(eq("memory-1"), eq("user-1"), any(Instant.class));
  }

  @Test
  void changingCheckinKeepsMemoryWithAnotherActiveSource() {
    MemoryMapper mapper = mock(MemoryMapper.class);
    MemorySourceMapper sources = mock(MemorySourceMapper.class);
    VectorSyncJobMapper jobs = mock(VectorSyncJobMapper.class);
    MemorySourceEntity source = new MemorySourceEntity();
    source.setMemoryId("memory-1");
    when(sources.listBySource("user-1", "checkin", "checkin-1")).thenReturn(List.of(source));
    when(sources.listByMemory("memory-1", "user-1")).thenReturn(List.of(new MemorySourceEntity()));
    var service = new MemoryService(mapper, mock(AiGatewayService.class), mock(IdentityService.class),
        mock(VectorStore.class), sources, jobs);

    service.deactivateCheckinSource("user-1", "checkin-1");

    verify(sources).deactivateBySource("user-1", "checkin", "checkin-1");
    verifyNoInteractions(mapper, jobs);
  }
}
