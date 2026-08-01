package com.today.media;

import com.today.identity.IdentityService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {

  private final LocalMediaStorage storage;
  private final IdentityService identity;

  public MediaController(LocalMediaStorage storage, IdentityService identity) {
    this.storage = storage;
    this.identity = identity;
  }

  @GetMapping("/v1/media/{*relativePath}")
  public ResponseEntity<Resource> getMedia(@PathVariable("relativePath") String relativePath) {
    String userId = identity.getCurrentUserId();
    String path = stripLeadingSlash(relativePath);
    // Punch photos are stored under punch/{userId}/...
    String expectedPrefix = "punch/" + userId.replaceAll("[^a-zA-Z0-9_-]", "_") + "/";
    if (!path.startsWith(expectedPrefix)) {
      return ResponseEntity.notFound().build();
    }
    Path file = storage.resolveReadable(path);
    String contentType;
    try {
      contentType = Files.probeContentType(file);
    } catch (Exception e) {
      contentType = null;
    }
    MediaType mediaType =
        contentType != null
            ? MediaType.parseMediaType(contentType)
            : MediaType.APPLICATION_OCTET_STREAM;
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
        .contentType(mediaType)
        .body(new FileSystemResource(file));
  }

  private static String stripLeadingSlash(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("^/+", "");
  }
}
