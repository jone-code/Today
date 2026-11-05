package com.today.proactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiResult;
import com.today.aigateway.AiGatewayService.AiTask;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinService;
import com.today.common.AiProvider;
import com.today.common.PromptSource;
import com.today.identity.IdentityService;
import com.today.memory.MemoryService;
import com.today.summary.SummaryService;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.today.persistence.ProactivePromptEventEntity;

class ProactiveAuthorizationTest {
  private final AiGatewayService ai = mock(AiGatewayService.class);
  private final CheckinService checkins = mock(CheckinService.class);
  private final ProactivePromptEventMapper events = mock(ProactivePromptEventMapper.class);
  private final ProactiveService service = new ProactiveService(ai, checkins,
      mock(MemoryService.class), mock(SummaryService.class), mock(IdentityService.class), events);

  @Test
  void modelFollowupCannotBypassAgentTopicAuthorization() {
    when(checkins.todayDate()).thenReturn(LocalDate.now().toString());
    when(ai.complete(eq(AiTask.proactive), any(), any(TypeReference.class), any(Supplier.class)))
        .thenReturn(new AiResult<>(new ProactiveService.ProactivePayload(List.of(
            new ProactivePromptDto("followup-interview", "面试怎么样？", null, PromptSource.followup),
            new ProactivePromptDto("gentle", "想从哪里开始？", null, PromptSource.gentle))), AiProvider.llm));

    var result = service.today();

    assertEquals(1, result.prompts().size());
    assertEquals(PromptSource.gentle, result.prompts().get(0).source());
    ArgumentCaptor<ProactivePromptEventEntity> stored = ArgumentCaptor.forClass(ProactivePromptEventEntity.class);
    verify(events).upsert(stored.capture());
    assertEquals("gentle", stored.getValue().getSource());
  }

  @Test
  void dailyRecordDoesNotCreateUnconsentedFollowupEvenWithoutModel() {
    LocalDate today = LocalDate.now();
    when(checkins.todayDate()).thenReturn(today.toString());
    when(checkins.listRecent(14)).thenReturn(List.of(new CheckinDto("c1", "u1",
        today.minusDays(1).toString(), "昨天参加了面试", "", "")));
    when(ai.complete(eq(AiTask.proactive), any(), any(TypeReference.class), any(Supplier.class)))
        .thenAnswer(invocation -> new AiResult<>(
            ((Supplier<?>) invocation.getArgument(3)).get(), AiProvider.heuristic));

    var result = service.today();

    assertTrue(result.prompts().stream().noneMatch(prompt -> prompt.source() == PromptSource.followup));
    assertTrue(result.prompts().stream().noneMatch(prompt -> prompt.text().contains("面试")));
  }

  @Test
  void modelReturningOnlyUnauthorizedFollowupStillShowsOrdinaryPrompt() {
    when(checkins.todayDate()).thenReturn(LocalDate.now().toString());
    when(ai.complete(eq(AiTask.proactive), any(), any(TypeReference.class), any(Supplier.class)))
        .thenReturn(new AiResult<>(new ProactiveService.ProactivePayload(List.of(
            new ProactivePromptDto("followup-interview", "面试怎么样？", null, PromptSource.followup))),
            AiProvider.llm));

    assertEquals(PromptSource.gentle, service.today().prompts().get(0).source());
  }
}
