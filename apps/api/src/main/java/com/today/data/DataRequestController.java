package com.today.data;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DataRequestController {
  @org.springframework.beans.factory.annotation.Value("${today.features.data-export-enabled:true}") private boolean exportEnabled = true;
  @org.springframework.beans.factory.annotation.Value("${today.features.account-delete-enabled:true}") private boolean deleteEnabled = true;
  private void enabled(boolean enabled) { if (!enabled) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "feature temporarily unavailable"); }
  private final DataRequestService service;
  private final com.today.common.CreateIdempotencyService idempotency;
  public DataRequestController(DataRequestService service, com.today.common.CreateIdempotencyService idempotency) {
    this.service=service; this.idempotency=idempotency;
  }
  @GetMapping("/v1/data-requests") public java.util.List<DataRequestDto> recent() { return service.recent(); }
  @PostMapping("/v1/data-requests/export")
  public DataRequestDto export(@RequestHeader("Idempotency-Key") String key) {
    enabled(exportEnabled);
    return idempotency.create("data_export", key, java.util.Map.of("type", "export"),
        DataRequestDto.class, service::createExport);
  }
  @PostMapping("/v1/data-requests/{id}/download-token")
  public java.util.Map<String, String> token(@PathVariable String id) {
    return java.util.Map.of("token", service.issueDownloadToken(id));
  }
  @GetMapping("/v1/data-requests/{id}") public DataRequestDto get(@PathVariable String id){return service.get(id);}
  @PostMapping("/v1/data-requests/{id}/retry") public DataRequestDto retry(@PathVariable String id){return service.retry(id);}
  @GetMapping("/v1/data-requests/{id}/download") public ResponseEntity<org.springframework.core.io.InputStreamResource> download(@PathVariable String id,@RequestParam String token) throws java.io.IOException {Path path=service.download(id,token); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("today-export.zip").build().toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(new org.springframework.core.io.InputStreamResource(service.openDownload(path)));}
  @PostMapping("/v1/data-requests/delete-account") public DataRequestDto delete(@RequestBody DeleteRequest request){enabled(deleteEnabled); return service.deleteAccount(request.phrase());}
  public record DeleteRequest(@NotBlank String phrase) {}
}
