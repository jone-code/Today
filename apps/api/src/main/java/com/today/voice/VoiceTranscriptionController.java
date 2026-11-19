package com.today.voice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VoiceTranscriptionController {
  private static final Set<String> AUDIO_TYPES = Set.of("audio/m4a", "audio/mp4", "audio/aac", "audio/wav", "audio/x-m4a");
  private final VoiceTranscriptionService service;
  public VoiceTranscriptionController(VoiceTranscriptionService service) { this.service = service; }

  @PostMapping(value = "/v1/voice/transcriptions", consumes = "multipart/form-data")
  public VoiceTranscriptionJobDto create(@RequestPart("audio") MultipartFile audio,
      @RequestParam(defaultValue = "0") long durationMs,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String clientRequestId) {
    if (clientRequestId == null || clientRequestId.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }
    if (audio == null || audio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audio is required");
    if (durationMs < 0 || durationMs > 120_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audio duration must be at most 120 seconds");
    String type = audio.getContentType() == null ? "" : audio.getContentType().split(";", 2)[0].toLowerCase(Locale.ROOT);
    if (!AUDIO_TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported audio type");
    return service.create(clientRequestId.trim(), type, durationMs, audio);
  }
  @GetMapping("/v1/voice/transcriptions/{id}")
  public VoiceTranscriptionJobDto get(@PathVariable String id) { return service.get(id); }
  @PostMapping("/v1/voice/transcriptions/{id}/retry")
  public VoiceTranscriptionJobDto retry(@PathVariable String id) { return service.retry(id); }
}
