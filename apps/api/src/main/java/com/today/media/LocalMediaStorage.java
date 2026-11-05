package com.today.media;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
  private static final int MAX_AVATAR_DIMENSION = 8192;
  private static final long MAX_AVATAR_PIXELS = 12_000_000L;
  private static final int OUTPUT_AVATAR_DIMENSION = 1024;

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
      deleteIfPresent(relative);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "failed to store photo: " + e.getMessage());
    }
  }

  public StoredAvatar storeAgentAvatar(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar is required");
    }
    if (file.getSize() > properties.getMaxBytes()) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "avatar exceeds max size");
    }
    String contentType = normalizeContentType(file.getContentType());
    if (!Set.of("image/jpeg", "image/png").contains(contentType)) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "avatar must be jpeg or png");
    }
    String safeUser = sanitizeSegment(userId);
    String relative = String.format(Locale.ROOT, "agent/%s/%s.jpg", safeUser, UUID.randomUUID());
    Path target = resolveSafe(relative);
    try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
      if (imageInput == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatar image");
      Iterator<ImageReader> available = ImageIO.getImageReaders(imageInput);
      if (!available.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatar image");
      ImageReader reader = available.next();
      BufferedImage decoded;
      int width;
      int height;
      try {
        reader.setInput(imageInput, true, true);
        String actualType = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!("image/jpeg".equals(contentType) && ("jpeg".equals(actualType) || "jpg".equals(actualType)))
            && !("image/png".equals(contentType) && "png".equals(actualType))) {
          throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "avatar image type does not match upload");
        }
        width = reader.getWidth(0);
        height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > MAX_AVATAR_DIMENSION
            || height > MAX_AVATAR_DIMENSION || (long) width * height > MAX_AVATAR_PIXELS) {
          throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "avatar dimensions exceed limit");
        }
        int sample = Math.max(1, (int) Math.ceil((double) Math.max(width, height) / OUTPUT_AVATAR_DIMENSION));
        ImageReadParam params = reader.getDefaultReadParam();
        params.setSourceSubsampling(sample, sample, 0, 0);
        decoded = reader.read(0, params);
        if (decoded == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatar image");
      } catch (IOException | IndexOutOfBoundsException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid avatar image");
      } finally {
        reader.dispose();
      }
      double scale = Math.min(1d, (double) OUTPUT_AVATAR_DIMENSION / Math.max(decoded.getWidth(), decoded.getHeight()));
      int outWidth = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
      int outHeight = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
      BufferedImage normalized = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = normalized.createGraphics();
      try {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, outWidth, outHeight);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(decoded, 0, 0, outWidth, outHeight, null);
      } finally {
        graphics.dispose();
      }
      Files.createDirectories(target.getParent());
      if (!ImageIO.write(normalized, "jpeg", target.toFile())) {
        throw new IOException("jpeg encoder unavailable");
      }
      long size = Files.size(target);
      if (size > properties.getMaxBytes()) {
        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "avatar exceeds max size");
      }
      return new StoredAvatar(relative, size, "image/jpeg");
    } catch (ResponseStatusException e) {
      deleteIfPresent(relative);
      throw e;
    } catch (IOException e) {
      deleteIfPresent(relative);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store avatar");
    }
  }

  public record StoredAvatar(String relativePath, long sizeBytes, String contentType) {}

  public boolean deleteIfPresent(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return true;
    }
    try {
      Path target = resolveSafe(relativePath);
      Files.deleteIfExists(target);
      return true;
    } catch (Exception e) {
      log.warn("failed to delete media {}: {}", relativePath, e.getMessage());
      return false;
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
