package com.today.voice;

import org.springframework.stereotype.Component;

/** Deliberate internal-trial fallback until a real provider is approved and configured. */
@Component
public class UnconfiguredTranscriptionProvider implements TranscriptionProvider {
  @Override
  public TranscriptionResult transcribe(String mediaPath, String contentType, long durationMs) {
    return TranscriptionResult.unavailable();
  }
}
