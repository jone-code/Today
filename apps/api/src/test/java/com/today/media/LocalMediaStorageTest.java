package com.today.media;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
  void rejectsPathTraversal() {
    assertThrows(ResponseStatusException.class, () -> storage.resolveReadable("../secret"));
  }
}
