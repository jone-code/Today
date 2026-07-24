package com.today.proactive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  @PostMapping("/v1/proactive/prompts/{id}/select")
  public ProactivePromptDto select(@PathVariable("id") String id) {
    return proactive.select(id);
  }

  @PostMapping("/v1/proactive/prompts/{id}/dismiss")
  public ProactivePromptDto dismiss(@PathVariable("id") String id) {
    return proactive.dismiss(id);
  }
}
