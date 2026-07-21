package com.today.memory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

  private final MemoryService memories;

  public MemoryController(MemoryService memories) {
    this.memories = memories;
  }

  @GetMapping("/v1/memories")
  public MemoryListDto list() {
    return new MemoryListDto(memories.list());
  }
}
