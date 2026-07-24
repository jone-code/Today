package com.today.memory;

import com.today.vector.VectorReindexResult;
import com.today.vector.VectorReindexService;
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
  private final VectorReindexService vectorReindex;

  public MemoryController(MemoryService memories, VectorReindexService vectorReindex) {
    this.memories = memories;
    this.vectorReindex = vectorReindex;
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

  /** 将当前用户记忆同步到向量索引；可选补齐缺失 embedding、重建索引结构。 */
  @PostMapping("/v1/memories/reindex")
  public VectorReindexResult reindex(
      @RequestParam(name = "fillMissingEmbeddings", defaultValue = "false")
          boolean fillMissingEmbeddings,
      @RequestParam(name = "recreate", defaultValue = "false") boolean recreate) {
    return vectorReindex.reindexCurrentUser(fillMissingEmbeddings, recreate);
  }
}
