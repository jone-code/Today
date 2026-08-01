package com.today.media;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalMediaStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalMediaStorage.class);

  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  private final MediaProperties properties;
  private Path root;

  public LocalMediaStorage(MediaProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void init() throws IOException {
    root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
    Files.createDirectories(root);
    log.info("media root: {}", root);
  }

  public Path getRoot() {
    return root;
  }

  /**
   * Store a punch photo under {@code punch/{userId}/{yyyy}/{MM}/{uuid}.ext}.
   *
   * @return relative path key (POSIX separators)
   */
  public String storePunchPhoto(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo is required");
    }
    if (file.getSize() > properties.getMaxBytes()) {
      throw new ResponseStatusException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "photo exceeds max size of " + properties.getMaxBytes() + " bytes");
    }
    String contentType = normalizeContentType(file.getContentType());
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported image type: " + contentType);
    }
    String safeUser = sanitizeSegment(userId);
    String ext = extensionFor(contentType, file.getOriginalFilename());
    java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
    String relative =
        String.format(
            Locale.ROOT,
            "punch/%s/%04d/%02d/%s.%s",
            safeUser,
            today.getYear(),
            today.getMonthValue(),
            UUID.randomUUID(),
            ext);
    Path target = resolveSafe(relative);
    try {
      Files.createDirectories(target.getParent());
      try (InputStream in = file.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return relative;
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "failed to store photo: " + e.getMessage());
    }
  }

  public void deleteIfPresent(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return;
    }
    try {
      Path target = resolveSafe(relativePath);
      Files.deleteIfExists(target);
    } catch (Exception e) {
      log.warn("failed to delete media {}: {}", relativePath, e.getMessage());
    }
  }

  public Path resolveReadable(String relativePath) {
    Path target = resolveSafe(relativePath);
    if (!Files.isRegularFile(target)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "media not found");
    }
    return target;
  }

  public String publicUrl(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return null;
    }
    return "/v1/media/" + relativePath.replace('\\', '/');
  }

  private Path resolveSafe(String relativePath) {
    String normalized = relativePath.replace('\\', '/').replaceAll("^/+", "");
    if (normalized.isBlank()
        || normalized.contains("..")
        || normalized.startsWith("/")
        || normalized.contains(":")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid media path");
    }
    Path target = root.resolve(normalized).normalize();
    if (!target.startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid media path");
    }
    return target;
  }

  private static String sanitizeSegment(String value) {
    String cleaned = value == null ? "" : value.replaceAll("[^a-zA-Z0-9_-]", "_");
    if (cleaned.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user id");
    }
    return cleaned;
  }

  private static String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return "";
    }
    return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  private static String extensionFor(String contentType, String originalName) {
    return switch (contentType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      case "image/gif" -> "gif";
      default -> {
        if (originalName != null && originalName.contains(".")) {
          String ext =
              originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
          if (ext.matches("[a-z0-9]{2,5}")) {
            yield ext;
          }
        }
        yield "bin";
      }
    };
  }
}
