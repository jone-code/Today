package com.today.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.aigateway.AiProperties;
import com.today.common.AiProvider;
import com.today.common.Mood;
import com.today.identity.IdentityService;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckinControllerAsyncTest {

  @Mock CheckinService checkins;
  @Mock SummaryService summaries;
  @Mock CheckinAiPipeline pipeline;
  @Mock CheckinAiJobService aiJobs;
  @Mock IdentityService identity;
  @Mock AiProperties aiProperties;

  @InjectMocks CheckinController controller;

  @Test
  void asyncModeEnqueuesJobAndReturnsProcessing() {
    CheckinDto checkin =
        new CheckinDto("c1", "u1", "2026-07-23", "今天很忙", "t0", "t1");
    CheckinAiJobDto job =
        new CheckinAiJobDto("j1", "c1", "2026-07-23", "pending", 0, 5, null);
    when(checkins.upsert(org.mockito.ArgumentMatchers.any())).thenReturn(checkin);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(aiProperties.isAsyncCheckin()).thenReturn(true);
    when(aiJobs.enqueueAndKick("u1", "c1", "2026-07-23", "今天很忙")).thenReturn(job);

    CheckinSubmitResult result = controller.create(new CheckinCreateInput("今天很忙", null));

    assertEquals(CheckinSubmitResult.STATUS_PROCESSING, result.status());
    assertEquals(null, result.summary());
    assertEquals("j1", result.aiJob().id());
    verify(pipeline, never())
        .processAfterCheckinSync(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void syncModeReturnsReadyWithSummary() {
    CheckinDto checkin =
        new CheckinDto("c1", "u1", "2026-07-23", "今天很忙", "t0", "t1");
    DaySummaryDto summary =
        new DaySummaryDto(
            "c1",
            "2026-07-23",
            List.of("忙完了"),
            Mood.okay,
            "平常",
            List.of("日常"),
            "今天被记住了",
            "忙完了",
            AiProvider.heuristic,
            "t2");
    when(checkins.upsert(org.mockito.ArgumentMatchers.any())).thenReturn(checkin);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(aiProperties.isAsyncCheckin()).thenReturn(false);
    when(summaries.getByDate("2026-07-23")).thenReturn(summary);

    CheckinSubmitResult result = controller.create(new CheckinCreateInput("今天很忙", null));

    assertEquals(CheckinSubmitResult.STATUS_READY, result.status());
    assertEquals(summary, result.summary());
    verify(pipeline).processAfterCheckinSync("u1", "c1", "2026-07-23", "今天很忙");
    verify(aiJobs, never())
        .enqueueAndKick(eq("u1"), anyString(), anyString(), anyString());
  }
}
