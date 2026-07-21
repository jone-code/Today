package com.today.checkin;

import com.today.memory.MemoryService;
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
  private final MemoryService memories;

  public CheckinController(
      CheckinService checkins, SummaryService summaries, MemoryService memories) {
    this.checkins = checkins;
    this.summaries = summaries;
    this.memories = memories;
  }

  @GetMapping("/v1/checkins/today")
  public CheckinTodayResponse today() {
    CheckinDto checkin = checkins.getToday();
    return new CheckinTodayResponse(checkin);
  }

  @PostMapping("/v1/checkins")
  public CheckinSubmitResult create(@Valid @RequestBody CheckinCreateInput input) {
    CheckinDto checkin = checkins.upsert(input);
    var summary =
        summaries.generateForCheckin(checkin.id(), checkin.date(), checkin.rawText());
    memories.upsertFromCheckin(checkin.rawText());
    return new CheckinSubmitResult(checkin, summary);
  }
}
