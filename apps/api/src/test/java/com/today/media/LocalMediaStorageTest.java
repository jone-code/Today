package com.today.media;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class LocalMediaStorageTest {

  @TempDir Path tempDir;

  private LocalMediaStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    MediaProperties props = new MediaProperties();
    props.setRoot(tempDir.toString());
    props.setMaxBytes(1024 * 1024);
    storage = new LocalMediaStorage(props);
    storage.init();
  }

  @Test
  void storesPunchPhotoUnderUserPrefix() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "photo", "shot.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {(byte) 0xff, (byte) 0xd8});
    String relative = storage.storePunchPhoto("user-1", file);
    assertTrue(relative.startsWith("punch/user-1/"));
    assertTrue(Files.isRegularFile(storage.resolveReadable(relative)));
  }

  @Test
  void interruptedPhotoWriteDoesNotLeaveAnOrphanFile() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(2L);
    when(file.getContentType()).thenReturn(MediaType.IMAGE_JPEG_VALUE);
    when(file.getOriginalFilename()).thenReturn("shot.jpg");
    when(file.getInputStream()).thenReturn(new InputStream() {
      private boolean sent;
      @Override public int read() throws IOException {
        if (sent) throw new IOException("interrupted");
        sent = true;
        return 0xff;
      }
    });
    assertThrows(ResponseStatusException.class, () -> storage.storePunchPhoto("u1", file));
    try (var files = Files.walk(tempDir)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
  }

  @Test
  void rejectsPathTraversal() {
    assertThrows(ResponseStatusException.class, () -> storage.resolveReadable("../secret"));
  }

  @Test
  void avatarIsReencodedAndDoesNotRetainInputMetadata() throws Exception {
    byte[] original = image("jpeg", 16, 12);
    byte[] marker = "private-location-tag".getBytes(StandardCharsets.US_ASCII);
    byte[] annotated = new byte[original.length + marker.length + 4];
    System.arraycopy(original, 0, annotated, 0, 2);
    annotated[2] = (byte) 0xff; annotated[3] = (byte) 0xfe;
    annotated[4] = (byte) ((marker.length + 2) >> 8);
    annotated[5] = (byte) (marker.length + 2);
    System.arraycopy(marker, 0, annotated, 6, marker.length);
    System.arraycopy(original, 2, annotated, 6 + marker.length, original.length - 2);

    var stored = storage.storeAgentAvatar("u1", new MockMultipartFile(
        "avatar", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, annotated));
    byte[] saved = Files.readAllBytes(storage.resolveReadable(stored.relativePath()));

    assertEquals("image/jpeg", stored.contentType());
    assertEquals(saved.length, stored.sizeBytes());
    assertTrue(stored.relativePath().startsWith("agent/u1/"));
    assertTrue(!new String(saved, StandardCharsets.ISO_8859_1).contains("private-location-tag"));
    assertEquals(16, ImageIO.read(storage.resolveReadable(stored.relativePath()).toFile()).getWidth());
  }

  @Test
  void transparentPngIsFlattenedIntoSafeJpeg() throws Exception {
    byte[] png = image("png", 12, 12);
    var stored = storage.storeAgentAvatar("u1", new MockMultipartFile("avatar", "image.png", "image/png", png));
    BufferedImage saved = ImageIO.read(storage.resolveReadable(stored.relativePath()).toFile());
    assertEquals("image/jpeg", stored.contentType());
    assertEquals(12, saved.getHeight());
    assertEquals(0xffffff, saved.getRGB(0, 0) & 0xffffff);
  }

  @Test
  void rejectsSpoofedCorruptAndOversizedImages() throws Exception {
    byte[] png = image("png", 16, 16);
    assertThrows(ResponseStatusException.class, () -> storage.storeAgentAvatar("u1",
        new MockMultipartFile("avatar", "fake.jpg", "image/jpeg", png)));
    assertThrows(ResponseStatusException.class, () -> storage.storeAgentAvatar("u1",
        new MockMultipartFile("avatar", "broken.jpg", "image/jpeg", new byte[] { 1, 2, 3 })));
    var truncated = assertThrows(ResponseStatusException.class, () -> storage.storeAgentAvatar("u1",
        new MockMultipartFile("avatar", "truncated.jpg", "image/jpeg",
            new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0 })));
    assertEquals(HttpStatus.BAD_REQUEST, truncated.getStatusCode());
    byte[] enormous = image("png", 8193, 1);
    assertThrows(ResponseStatusException.class, () -> storage.storeAgentAvatar("u1",
        new MockMultipartFile("avatar", "wide.png", "image/png", enormous)));
  }

  private static byte[] image(String format, int width, int height) throws Exception {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    if ("jpeg".equals(format)) {
      BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      var graphics = rgb.createGraphics();
      graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height); graphics.dispose();
      img = rgb;
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(img, format, output);
    return output.toByteArray();
  }
}
