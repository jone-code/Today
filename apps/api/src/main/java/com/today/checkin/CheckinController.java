package com.today.checkin;

import com.today.aigateway.AiProperties;
import com.today.identity.IdentityService;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckinController {

  private final CheckinService checkins;
  private final SummaryService summaries;
  private final CheckinAiPipeline pipeline;
  private final IdentityService identity;
  private final AiProperties aiProperties;

  public CheckinController(
      CheckinService checkins,
      SummaryService summaries,
      CheckinAiPipeline pipeline,
      IdentityService identity,
      AiProperties aiProperties) {
    this.checkins = checkins;
    this.summaries = summaries;
    this.pipeline = pipeline;
    this.identity = identity;
    this.aiProperties = aiProperties;
  }

  @GetMapping("/v1/checkins/today")
  public CheckinTodayResponse today() {
    CheckinDto checkin = checkins.getToday();
    return new CheckinTodayResponse(checkin);
  }

  @PostMapping("/v1/checkins")
  public CheckinSubmitResult create(@Valid @RequestBody CheckinCreateInput input) {
    CheckinDto checkin = checkins.upsert(input);
    String userId = identity.getCurrentUserId();

    if (aiProperties.isAsyncCheckin()) {
      pipeline.processAfterCheckin(userId, checkin.id(), checkin.date(), checkin.rawText());
      return new CheckinSubmitResult(
          checkin, null, CheckinSubmitResult.STATUS_PROCESSING);
    }

    pipeline.processAfterCheckinSync(userId, checkin.id(), checkin.date(), checkin.rawText());
    DaySummaryDto summary = summaries.getByDate(checkin.date());
    return new CheckinSubmitResult(checkin, summary, CheckinSubmitResult.STATUS_READY);
  }
}
