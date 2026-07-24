package com.today.memory;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemoryController {

  private final MemoryService memories;

  public MemoryController(MemoryService memories) {
    this.memories = memories;
  }

  @GetMapping("/v1/memories")
  public MemoryListDto list(
      @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
    return new MemoryListDto(memories.list(includeArchived));
  }

  @PutMapping("/v1/memories/{id}")
  public MemoryDto update(@PathVariable String id, @Valid @RequestBody MemoryUpdateRequest body) {
    return memories.update(id, body);
  }

  @PostMapping("/v1/memories/{id}/archive")
  public MemoryDto archive(@PathVariable String id) {
    return memories.archive(id, true);
  }

  @PostMapping("/v1/memories/{id}/unarchive")
  public MemoryDto unarchive(@PathVariable String id) {
    return memories.archive(id, false);
  }

  @DeleteMapping("/v1/memories/{id}")
  public void delete(@PathVariable String id) {
    memories.delete(id);
  }
}
