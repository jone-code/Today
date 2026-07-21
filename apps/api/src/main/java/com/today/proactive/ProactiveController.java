package com.today.proactive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProactiveController {

  private final ProactiveService proactive;

  public ProactiveController(ProactiveService proactive) {
    this.proactive = proactive;
  }

  @GetMapping("/v1/proactive/today")
  public ProactiveTodayDto today() {
    return proactive.today();
  }
}
