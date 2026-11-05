package com.today.summary;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.aigateway.AiGatewayService;
import com.today.aigateway.AiGatewayService.AiResult;
import com.today.checkin.CheckinMapper;
import com.today.checkin.StaleCheckinException;
import com.today.common.AiProvider;
import com.today.identity.IdentityService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.DaySummaryEntity;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SummaryServiceTest {
  @Test
  void lateModelResultCannotOverwriteUpdatedCheckin() {
    SummaryMapper summaries = mock(SummaryMapper.class);
    CheckinMapper checkins = mock(CheckinMapper.class);
    AiGatewayService ai = mock(AiGatewayService.class);
    CheckinEntity current = new CheckinEntity();
    current.setRawText("updated text");
    when(checkins.lockById("c1", "u1")).thenReturn(current);
    when(ai.complete(any(), any(), any(), any())).thenAnswer(invocation ->
        new AiResult<>(((Supplier<?>) invocation.getArgument(3)).get(), AiProvider.heuristic));

    SummaryService service = new SummaryService(summaries, checkins, ai, mock(IdentityService.class));
    assertThrows(StaleCheckinException.class,
        () -> service.generateForCheckin("u1", "c1", "2026-11-02", "old text"));

    verify(summaries, never()).upsert(any(DaySummaryEntity.class));
  }
}
