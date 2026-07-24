package com.today.vector;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VectorAdminController {

  private final VectorReindexService reindexService;
  private final VectorProperties properties;

  public VectorAdminController(VectorReindexService reindexService, VectorProperties properties) {
    this.reindexService = reindexService;
    this.properties = properties;
  }

  /**
   * 全量同步 MySQL memories → VectorStore。
   *
   * <p>若配置了 {@code today.vector.admin-token}，必须带请求头 {@code X-Today-Admin-Token}。
   */
  @PostMapping("/v1/admin/vector/reindex")
  public VectorReindexResult reindexAll(
      @RequestHeader(value = "X-Today-Admin-Token", required = false) String adminToken,
      @RequestParam(name = "fillMissingEmbeddings", defaultValue = "false")
          boolean fillMissingEmbeddings,
      @RequestParam(name = "recreate", defaultValue = "false") boolean recreate) {
    assertAdmin(adminToken);
    return reindexService.reindexAll(fillMissingEmbeddings, recreate);
  }

  private void assertAdmin(String adminToken) {
    if (!properties.hasAdminToken()) {
      // 本地未配置口令：仍要求已登录（Security 层），允许运维脚本
      return;
    }
    if (adminToken == null || !properties.getAdminToken().equals(adminToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid admin token");
    }
  }
}
