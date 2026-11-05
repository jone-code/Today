package com.today.checkin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.persistence.CheckinAiJobEntity;
import com.today.proactive.ProactiveService;
import com.today.summary.SummaryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckinAiPipelineTest {
  @Test
  void staleJobDoesNotCompleteOrFailReplacementJob() {
    SummaryService summaries = mock(SummaryService.class);
    CheckinMemoryCandidateService candidates = mock(CheckinMemoryCandidateService.class);
    ProactiveService proactive = mock(ProactiveService.class);
    CheckinAiJobMapper jobs = mock(CheckinAiJobMapper.class);
    CheckinService checkins = mock(CheckinService.class);
    CheckinAiJobService jobService = mock(CheckinAiJobService.class);
    CheckinAiJobEntity job = new CheckinAiJobEntity();
    job.setId("j1"); job.setStatus("running"); job.setLockedAt(Instant.parse("2026-11-02T09:00:00Z"));
    job.setCheckinId("c1"); job.setUserId("u1");
    job.setCheckinDate(java.time.LocalDate.of(2026, 11, 2));
    when(jobs.findByCheckinId("c1")).thenReturn(job);
    when(checkins.findByIdForPipeline("c1")).thenReturn(new CheckinDto(
        "c1", "u1", "2026-11-02", "old text", "2026-11-02T09:00:00Z", "2026-11-02T09:00:00Z"));
    doThrow(new StaleCheckinException()).when(summaries)
        .generateForCheckin("u1", "c1", "2026-11-02", "old text");

    new CheckinAiPipeline(summaries, candidates, proactive, jobs, checkins, jobService)
        .processClaimedJob(job);

    verify(jobService, never()).markSucceeded(any(), any());
    verify(jobService, never()).markFailed(any(), any(), anyInt(), anyInt(), any());
    verify(jobService).rescheduleStale("j1", job.getLockedAt());
    verify(candidates, never()).generate(any(), any(), any());
  }
}
