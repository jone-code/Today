package com.today.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.common.RateLimitService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class VoiceTranscriptionServiceTest {
  private final VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final LocalMediaStorage media = mock(LocalMediaStorage.class);
  private final VoiceTranscriptionService service = new VoiceTranscriptionService(mapper, identity, media);

  @Test
  void unconfiguredProviderReturnsExplicitRetryableFailure() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var result = service.create("request-1", "audio/m4a", 1000);
    assertEquals("failed", result.status());
    assertEquals("provider_unconfigured", result.errorCode());
    assertTrue(result.retryable());
    verify(mapper).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void sameRequestReturnsExistingJobWithoutCreatingDuplicate() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    VoiceTranscriptionJobEntity existing = new VoiceTranscriptionJobEntity();
    existing.setId("job-1"); existing.setStatus("failed"); existing.setErrorCode("provider_unconfigured");
    existing.setContentType("audio/m4a"); existing.setDurationMs(1000);
    existing.setAttempts(1); existing.setCreatedAt(Instant.now()); existing.setUpdatedAt(Instant.now());
    existing.setExpiresAt(Instant.now().plusSeconds(60));
    when(mapper.findByClientRequestId("u1", "request-1")).thenReturn(existing);
    var result = service.create("request-1", "audio/m4a", 1000);
    assertEquals("job-1", result.id());
    verify(mapper, never()).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void sameRequestWithDifferentAudioMetadataReturnsConflict() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    VoiceTranscriptionJobEntity existing = new VoiceTranscriptionJobEntity();
    existing.setId("job-1"); existing.setContentType("audio/m4a"); existing.setDurationMs(1000);
    existing.setStatus("failed"); existing.setErrorCode("provider_unconfigured");
    when(mapper.findByClientRequestId("u1", "request-1")).thenReturn(existing);

    var failure = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> service.create("request-1", "audio/wav", 1000));
    assertEquals(409, failure.getStatusCode().value());
    verify(mapper, never()).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void acceptedAudioIsStoredForShortLivedTranscription() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var audio = new MockMultipartFile("audio", "note.m4a", "audio/m4a", new byte[] {1, 2, 3});
    when(media.storeVoiceAudio("u1", audio)).thenReturn("voice/u1/job.m4a");
    service.create("request-audio", "audio/m4a", 1000, audio);
    verify(media).storeVoiceAudio("u1", audio);
    verify(mapper).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void sameMetadataWithDifferentAudioBytesReturnsConflict() throws Exception {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var first = new MockMultipartFile("audio", "one.m4a", "audio/m4a", new byte[] {1, 2, 3});
    var second = new MockMultipartFile("audio", "two.m4a", "audio/m4a", new byte[] {3, 2, 1});
    VoiceTranscriptionJobEntity existing = new VoiceTranscriptionJobEntity();
    existing.setId("job-1"); existing.setContentType("audio/m4a"); existing.setDurationMs(1000);
    existing.setPayloadHash(sha256(first.getBytes())); existing.setStatus("failed");
    when(mapper.findByClientRequestId("u1", "request-1")).thenReturn(existing);

    var failure = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> service.create("request-1", "audio/m4a", 1000, second));
    assertEquals(409, failure.getStatusCode().value());
    verify(mapper, never()).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void providerExceptionBecomesRetryableFailure() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TranscriptionProvider provider = mock(TranscriptionProvider.class);
    when(provider.transcribe(null, "audio/m4a", 1000)).thenThrow(new IllegalStateException("offline"));
    var serviceWithProvider = new VoiceTranscriptionService(mapper, identity, media, provider);
    var result = serviceWithProvider.create("request-provider", "audio/m4a", 1000);
    assertEquals("failed", result.status());
    assertEquals("provider_error", result.errorCode());
    assertTrue(result.retryable());
    verify(mapper).insert(any(VoiceTranscriptionJobEntity.class));
  }

  @Test
  void retryInvokesProviderAndCleansAudioAfterSuccess() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TranscriptionProvider provider = mock(TranscriptionProvider.class);
    var job = new VoiceTranscriptionJobEntity();
    job.setId("job-retry"); job.setUserId("u1"); job.setStatus("failed");
    job.setContentType("audio/m4a"); job.setDurationMs(1000); job.setMediaPath("voice/u1/retry.m4a");
    when(mapper.findOwnedById("u1", "job-retry")).thenReturn(job);
    when(mapper.markRetrying(eq("u1"), eq("job-retry"), any())).thenReturn(1);
    when(provider.transcribe("voice/u1/retry.m4a", "audio/m4a", 1000))
        .thenReturn(new TranscriptionProvider.TranscriptionResult("完成了记录", null));
    when(media.deleteIfPresent("voice/u1/retry.m4a")).thenReturn(true);
    when(mapper.updateOutcome(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    var serviceWithProvider = new VoiceTranscriptionService(mapper, identity, media, provider);

    var result = serviceWithProvider.retry("job-retry");

    assertEquals("succeeded", result.status());
    assertEquals("完成了记录", result.transcript());
    verify(media).deleteIfPresent("voice/u1/retry.m4a");
    verify(mapper).updateOutcome(eq("u1"), eq("job-retry"), eq("succeeded"), eq("完成了记录"),
        eq(null), eq(null), any(), any());
  }

  @Test
  void retryDoesNotCallProviderWhenAnotherWorkerOwnsTheJob() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TranscriptionProvider provider = mock(TranscriptionProvider.class);
    var job = new VoiceTranscriptionJobEntity();
    job.setId("job-running"); job.setStatus("transcribing");
    when(mapper.findOwnedById("u1", "job-running")).thenReturn(job);
    when(mapper.markRetrying(eq("u1"), eq("job-running"), any())).thenReturn(0);
    var serviceWithProvider = new VoiceTranscriptionService(mapper, identity, media, provider);

    var result = serviceWithProvider.retry("job-running");

    assertEquals("transcribing", result.status());
    org.mockito.Mockito.verifyNoInteractions(provider);
  }

  @Test
  void retryNormalizesMissingProviderErrorCode() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TranscriptionProvider provider = mock(TranscriptionProvider.class);
    var job = new VoiceTranscriptionJobEntity();
    job.setId("job-empty"); job.setStatus("failed"); job.setContentType("audio/m4a"); job.setDurationMs(1000);
    when(mapper.findOwnedById("u1", "job-empty")).thenReturn(job);
    when(mapper.markRetrying(eq("u1"), eq("job-empty"), any())).thenReturn(1);
    when(provider.transcribe(null, "audio/m4a", 1000))
        .thenReturn(new TranscriptionProvider.TranscriptionResult(null, null));
    when(mapper.updateOutcome(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    var result = new VoiceTranscriptionService(mapper, identity, media, provider).retry("job-empty");

    assertEquals("failed", result.status());
    assertEquals("provider_failed", result.errorCode());
  }

  @Test
  void retryStopsAfterMaximumAttempts() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    var job = new VoiceTranscriptionJobEntity();
    job.setId("job-limit"); job.setStatus("failed"); job.setAttempts(3);
    when(mapper.findOwnedById("u1", "job-limit")).thenReturn(job);
    var failure = org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> service.retry("job-limit"));
    assertEquals(409, failure.getStatusCode().value());
  }

  @Test
  void idempotentExistingRequestDoesNotConsumeVoiceRateLimit() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    RateLimitService limits = mock(RateLimitService.class);
    VoiceTranscriptionJobEntity existing = new VoiceTranscriptionJobEntity();
    existing.setId("job-existing"); existing.setStatus("failed"); existing.setContentType("audio/m4a");
    existing.setDurationMs(1000); existing.setPayloadHash(null);
    when(mapper.findByClientRequestId("u1", "same-request")).thenReturn(existing);

    var result = new VoiceTranscriptionService(mapper, identity, media,
        new UnconfiguredTranscriptionProvider(), limits).create("same-request", "audio/m4a", 1000);

    assertEquals("job-existing", result.id());
    org.mockito.Mockito.verifyNoInteractions(limits);
  }

  private static String sha256(byte[] bytes) throws Exception {
    var digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
    var out = new StringBuilder(64);
    for (byte value : digest) out.append(String.format("%02x", value));
    return out.toString();
  }
}
