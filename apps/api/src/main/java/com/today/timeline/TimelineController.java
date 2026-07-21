package com.today.timeline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimelineController {

  private final TimelineService timeline;

  public TimelineController(TimelineService timeline) {
    this.timeline = timeline;
  }

  @GetMapping("/v1/timeline")
  public TimelinePageDto list(@RequestParam(required = false) Integer limit) {
    int n = limit != null && limit > 0 ? limit : 30;
    return timeline.list(n);
  }
}
