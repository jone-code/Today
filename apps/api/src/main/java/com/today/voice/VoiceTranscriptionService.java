package com.today.voice;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.common.RateLimitService;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceTranscriptionService {
  private static final int MAX_ATTEMPTS = 3;
  private final VoiceTranscriptionMapper mapper; private final IdentityService identity; private final LocalMediaStorage media; private final TranscriptionProvider provider;
  private final RateLimitService rateLimits;
  @org.springframework.beans.factory.annotation.Value("${today.rate-limit.voice-per-hour:20}") private int voicePerHour = 20;
  @org.springframework.beans.factory.annotation.Value("${today.rate-limit.voice-per-day:100}") private int voicePerDay = 100;
  public VoiceTranscriptionService(VoiceTranscriptionMapper mapper, IdentityService identity, LocalMediaStorage media) {
    this(mapper, identity, media, new UnconfiguredTranscriptionProvider(), null);
  }
  public VoiceTranscriptionService(VoiceTranscriptionMapper mapper, IdentityService identity, LocalMediaStorage media, TranscriptionProvider provider) {
    this(mapper, identity, media, provider, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public VoiceTranscriptionService(VoiceTranscriptionMapper mapper, IdentityService identity, LocalMediaStorage media, TranscriptionProvider provider, RateLimitService rateLimits) {
    this.mapper = mapper; this.identity = identity; this.media = media; this.provider = provider; this.rateLimits = rateLimits;
  }

  @Transactional
  public VoiceTranscriptionJobDto create(String clientRequestId, String contentType, long durationMs) {
    return create(clientRequestId, contentType, durationMs, null);
  }

  @Transactional
  public VoiceTranscriptionJobDto create(String clientRequestId, String contentType, long durationMs, MultipartFile audio) {
    String userId = identity.getCurrentUserId();
    String payloadHash = hash(audio);
    VoiceTranscriptionJobEntity existing = mapper.findByClientRequestId(userId, clientRequestId);
    if (existing != null) {
      if (!java.util.Objects.equals(existing.getContentType(), contentType)
          || existing.getDurationMs() != durationMs
          || (existing.getPayloadHash() != null && payloadHash != null
              && !existing.getPayloadHash().equals(payloadHash))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "transcription request already used with different audio");
      }
      return dto(existing);
    }
    if (rateLimits != null) {
      rateLimits.check("voice_transcription_hour", userId, voicePerHour, Duration.ofHours(1));
      rateLimits.check("voice_transcription_day", userId, voicePerDay, Duration.ofDays(1));
    }
    Instant now = EntityMapper.now();
    VoiceTranscriptionJobEntity job = new VoiceTranscriptionJobEntity();
    job.setId(UUID.randomUUID().toString()); job.setUserId(userId); job.setClientRequestId(clientRequestId);
    job.setStatus("failed"); job.setContentType(contentType); job.setDurationMs(durationMs);
    job.setPayloadHash(payloadHash);
    if (audio != null) job.setMediaPath(media.storeVoiceAudio(userId, audio));
    job.setAttempts(1); job.setCreatedAt(now); job.setUpdatedAt(now);
    job.setCompletedAt(now); job.setExpiresAt(now.plus(Duration.ofHours(24)));
    TranscriptionProvider.TranscriptionResult result;
    try {
      result = provider.transcribe(job.getMediaPath(), contentType, durationMs);
    } catch (RuntimeException failure) {
      result = new TranscriptionProvider.TranscriptionResult(null, "provider_error");
    }
    if (result.succeeded()) {
      job.setStatus("succeeded"); job.setTranscript(result.transcript()); job.setCompletedAt(now);
      if (job.getMediaPath() != null) media.deleteIfPresent(job.getMediaPath());
      job.setMediaPath(null);
    } else {
      job.setStatus("failed"); job.setErrorCode(result.errorCode() == null ? "provider_failed" : result.errorCode());
    }
    try { mapper.insert(job); } catch (DuplicateKeyException duplicate) {
      VoiceTranscriptionJobEntity concurrent = mapper.findByClientRequestId(userId, clientRequestId);
      if (concurrent != null) return dto(concurrent); throw duplicate;
    }
    return dto(job);
  }

  private static String hash(MultipartFile audio) {
    if (audio == null) return null;
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(audio.getBytes());
      StringBuilder out = new StringBuilder(64);
      for (byte value : digest) out.append(String.format("%02x", value));
      return out.toString();
    } catch (NoSuchAlgorithmException | java.io.IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audio could not be read");
    }
  }

  @Transactional
  public VoiceTranscriptionJobDto retry(String id) {
    String userId = identity.getCurrentUserId(); VoiceTranscriptionJobEntity job = mapper.findOwnedById(userId, id);
    if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transcription not found");
    if (!"failed".equals(job.getStatus()) && !"expired".equals(job.getStatus())) return dto(job);
    if (job.getAttempts() >= MAX_ATTEMPTS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "transcription retry limit reached");
    }
    if (mapper.markRetrying(userId, id, EntityMapper.now()) != 1) {
      VoiceTranscriptionJobEntity current = mapper.findOwnedById(userId, id);
      if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transcription not found");
      return dto(current);
    }
    VoiceTranscriptionJobEntity refreshed = mapper.findOwnedById(userId, id);
    Instant now = EntityMapper.now();
    TranscriptionProvider.TranscriptionResult result;
    try {
      result = provider.transcribe(refreshed.getMediaPath(), refreshed.getContentType(), refreshed.getDurationMs());
    } catch (RuntimeException failure) {
      result = new TranscriptionProvider.TranscriptionResult(null, "provider_error");
    }
    boolean succeeded = result.succeeded();
    String errorCode = succeeded ? null : (result.errorCode() == null ? "provider_failed" : result.errorCode());
    String mediaPath = refreshed.getMediaPath();
    if (succeeded && mediaPath != null && media.deleteIfPresent(mediaPath)) mediaPath = null;
    int updated = mapper.updateOutcome(userId, id, succeeded ? "succeeded" : "failed", result.transcript(),
        errorCode, mediaPath, now, now);
    if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "transcription changed during retry");
    refreshed.setStatus(succeeded ? "succeeded" : "failed");
    refreshed.setTranscript(result.transcript()); refreshed.setErrorCode(errorCode);
    refreshed.setMediaPath(mediaPath); refreshed.setCompletedAt(now);
    return dto(refreshed);
  }

  public VoiceTranscriptionJobDto get(String id) {
    VoiceTranscriptionJobEntity job = mapper.findOwnedById(identity.getCurrentUserId(), id);
    if (job == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transcription not found");
    return dto(job);
  }

  private static VoiceTranscriptionJobDto dto(VoiceTranscriptionJobEntity job) {
    return new VoiceTranscriptionJobDto(job.getId(), job.getStatus(), job.getTranscript(), job.getErrorCode(), job.getAttempts(),
        job.getCreatedAt(), job.getUpdatedAt(), job.getExpiresAt(), job.getCompletedAt(),
        ("failed".equals(job.getStatus()) || "expired".equals(job.getStatus()))
            && job.getAttempts() < MAX_ATTEMPTS);
  }
}
