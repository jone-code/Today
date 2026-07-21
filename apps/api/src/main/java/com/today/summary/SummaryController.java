package com.today.summary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/summaries")
public class SummaryController {

  private final SummaryService summaries;

  public SummaryController(SummaryService summaries) {
    this.summaries = summaries;
  }

  @GetMapping("/{date}")
  public DaySummaryDto getByDate(@PathVariable String date) {
    return summaries.getByDate(date);
  }
}
