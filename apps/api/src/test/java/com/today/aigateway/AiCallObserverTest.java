package com.today.aigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.common.AiProvider;
import com.today.persistence.AiCallLogEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCallObserverTest {

  @Mock AiCallLogMapper logs;
  @Mock AiProperties properties;

  @InjectMocks AiCallObserver observer;

  @Test
  void recordCompletePersistsAndCounts() {
    observer.recordComplete(AiGatewayService.AiTask.summary, AiProvider.llm, "ok", 42, null);
    observer.recordComplete(
        AiGatewayService.AiTask.proactive, AiProvider.heuristic, "fallback", 10, "boom");

    Map<String, Object> stats = observer.processStats();
    assertEquals(1L, stats.get("ok"));
    assertEquals(1L, stats.get("fallback"));

    ArgumentCaptor<AiCallLogEntity> captor = ArgumentCaptor.forClass(AiCallLogEntity.class);
    verify(logs, org.mockito.Mockito.times(2)).insert(captor.capture());
    List<AiCallLogEntity> all = captor.getAllValues();
    assertEquals("ok", all.get(0).getOutcome());
    assertEquals("fallback", all.get(1).getOutcome());
  }

  @Test
  void statsSinceHoursAggregates() {
    when(logs.aggregateSince(any()))
        .thenReturn(
            List.of(
                Map.of(
                    "kind",
                    "complete",
                    "task",
                    "summary",
                    "outcome",
                    "ok",
                    "cnt",
                    2,
                    "avgElapsedMs",
                    30.0,
                    "maxElapsedMs",
                    40)));
    when(logs.avgElapsedSince(any(), org.mockito.ArgumentMatchers.eq("ok"))).thenReturn(30.0);

    Map<String, Object> stats = observer.statsSinceHours(24);
    assertEquals(2L, stats.get("total"));
    assertEquals(30.0, stats.get("avgOkElapsedMs"));
  }
}
