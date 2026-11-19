package com.today.voice;

/** Provider boundary for transcription; providers must not write business records. */
public interface TranscriptionProvider {
  TranscriptionResult transcribe(String mediaPath, String contentType, long durationMs);

  record TranscriptionResult(String transcript, String errorCode) {
    public boolean succeeded() { return transcript != null && !transcript.isBlank() && errorCode == null; }
    public static TranscriptionResult unavailable() { return new TranscriptionResult(null, "provider_unconfigured"); }
  }
}
