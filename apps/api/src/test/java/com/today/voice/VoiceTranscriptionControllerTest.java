package com.today.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class VoiceTranscriptionControllerTest {
  private final VoiceTranscriptionService service = mock(VoiceTranscriptionService.class);
  private final VoiceTranscriptionController controller = new VoiceTranscriptionController(service);

  @Test
  void rejectsEmptyAudio() {
    var failure = assertThrows(ResponseStatusException.class, () -> controller.create(
        new MockMultipartFile("audio", "empty.m4a", "audio/m4a", new byte[0]), 1000, "request-1"));
    assertEquals(400, failure.getStatusCode().value());
  }

  @Test
  void rejectsAudioLongerThanTwoMinutes() {
    var audio = new MockMultipartFile("audio", "note.m4a", "audio/m4a", new byte[] {1});
    var failure = assertThrows(ResponseStatusException.class, () -> controller.create(audio, 120_001, "request-1"));
    assertEquals(400, failure.getStatusCode().value());
  }

  @Test
  void rejectsUnsupportedContentType() {
    var audio = new MockMultipartFile("audio", "note.txt", "text/plain", new byte[] {1});
    var failure = assertThrows(ResponseStatusException.class, () -> controller.create(audio, 1000, "request-1"));
    assertEquals(415, failure.getStatusCode().value());
  }

  @Test
  void rejectsBlankIdempotencyKey() {
    var audio = new MockMultipartFile("audio", "note.m4a", "audio/m4a", new byte[] {1});
    var failure = assertThrows(ResponseStatusException.class, () -> controller.create(audio, 1000, "  "));
    assertEquals(400, failure.getStatusCode().value());
  }
}
