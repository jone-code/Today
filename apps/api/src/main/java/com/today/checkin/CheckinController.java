package com.today.checkin;

import com.today.aigateway.AiProperties;
import com.today.common.CreateIdempotencyService;
import com.today.identity.IdentityService;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CheckinController {

  private final CheckinService checkins;
  private final SummaryService summaries;
  private final CheckinAiPipeline pipeline;
  private final CheckinAiJobService aiJobs;
  private final IdentityService identity;
  private final AiProperties aiProperties;
  private final CheckinMemoryCandidateService memoryCandidates;
  private final CreateIdempotencyService requests;

  public CheckinController(
      CheckinService checkins,
      SummaryService summaries,
      CheckinAiPipeline pipeline,
      CheckinAiJobService aiJobs,
      IdentityService identity,
      AiProperties aiProperties,
      CheckinMemoryCandidateService memoryCandidates,
      CreateIdempotencyService requests) {
    this.checkins = checkins;
    this.summaries = summaries;
    this.pipeline = pipeline;
    this.aiJobs = aiJobs;
    this.identity = identity;
    this.aiProperties = aiProperties;
    this.memoryCandidates = memoryCandidates;
    this.requests = requests;
  }

  @GetMapping("/v1/checkins/today")
  public CheckinTodayResponse today() {
    CheckinDto checkin = checkins.getToday();
    CheckinAiJobDto job =
        checkin == null ? null : aiJobs.findByCheckinId(checkin.id());
    return new CheckinTodayResponse(checkin, job);
  }

  @GetMapping("/v1/checkins/{id}")
  public CheckinDto byId(@PathVariable String id) { return checkins.getById(id); }

  @GetMapping("/v1/checkins/{id}/memory-candidates")
  public List<CheckinMemoryCandidateDto> memoryCandidates(@PathVariable String id) {
    return memoryCandidates.list(id);
  }

  @PostMapping("/v1/checkins/memory-candidates/{id}/confirm")
  public CheckinMemoryCandidateDto confirmMemoryCandidate(
      @PathVariable String id, @RequestBody(required = false) CandidateConfirmInput input) {
    if (input != null && input.text() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory text is required");
    }
    return memoryCandidates.confirm(id, input == null ? null : input.text());
  }

  public record CandidateConfirmInput(String text) {}

  @PostMapping("/v1/checkins/memory-candidates/{id}/reject")
  public CheckinMemoryCandidateDto rejectMemoryCandidate(@PathVariable String id) {
    return memoryCandidates.reject(id);
  }

  @PostMapping("/v1/checkins")
  public CheckinSubmitResult create(@Valid @RequestBody CheckinCreateInput input,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return requests.create("checkin", key, input, CheckinSubmitResult.class, () -> createOnce(input));
  }

  private CheckinSubmitResult createOnce(CheckinCreateInput input) {
    CheckinDto checkin = checkins.upsert(input);
    String userId = identity.getCurrentUserId();

    if (aiProperties.isAsyncCheckin()) {
      CheckinAiJobDto job =
          aiJobs.enqueueAndKick(userId, checkin.id(), checkin.date(), checkin.rawText());
      return new CheckinSubmitResult(
          checkin, null, CheckinSubmitResult.STATUS_PROCESSING, job);
    }

    pipeline.processAfterCheckinSync(userId, checkin.id(), checkin.date(), checkin.rawText());
    DaySummaryDto summary = summaries.getByDate(checkin.date());
    return new CheckinSubmitResult(checkin, summary, CheckinSubmitResult.STATUS_READY, null);
  }

  /** 手动重跑今日 AI 流水线（失败后「继续整理」） */
  @PostMapping("/v1/checkins/today/reprocess")
  public CheckinSubmitResult reprocessToday() {
    CheckinDto checkin = checkins.getToday();
    if (checkin == null) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "today checkin not found");
    }
    CheckinAiJobDto job =
        aiJobs.requestRetry(identity.getCurrentUserId(), checkin.id());
    return new CheckinSubmitResult(
        checkin, null, CheckinSubmitResult.STATUS_PROCESSING, job);
  }
}
