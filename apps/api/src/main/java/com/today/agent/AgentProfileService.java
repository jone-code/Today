package com.today.agent;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.media.LocalMediaStorage;
import com.today.persistence.AgentMediaEntity;
import com.today.persistence.AgentProfileEntity;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AgentProfileService {
  private static final String DEFAULT_NAME = "Today";
  private static final String DEFAULT_TONE = "温和、好奇、有分寸";

  private final AgentProfileMapper mapper;
  private final IdentityService identity;
  private final AgentMediaMapper mediaMapper;
  private final LocalMediaStorage mediaStorage;

  public AgentProfileService(AgentProfileMapper mapper, IdentityService identity, AgentMediaMapper mediaMapper, LocalMediaStorage mediaStorage) {
    this.mapper = mapper;
    this.identity = identity;
    this.mediaMapper = mediaMapper;
    this.mediaStorage = mediaStorage;
  }

  @Transactional
  public AgentProfileDto getOrCreate() {
    String userId = identity.getCurrentUserId();
    AgentProfileEntity existing = mapper.findByUserId(userId);
    if (existing == null) {
      Instant now = EntityMapper.now();
      existing = new AgentProfileEntity();
      existing.setUserId(userId);
      existing.setName(DEFAULT_NAME);
      existing.setTone(DEFAULT_TONE);
      existing.setVersion(1);
      existing.setCreatedAt(now);
      existing.setUpdatedAt(now);
      mapper.insert(existing);
      existing = mapper.lockByUserId(userId);
    }
    return toDto(existing);
  }

  @Transactional
  public AgentProfileDto update(AgentProfileUpdateRequest input) {
    AgentProfileEntity entity = requireLocked();
    if (input.name() != null && !input.name().isBlank()) entity.setName(input.name().trim());
    if (input.tone() != null && !input.tone().isBlank()) entity.setTone(input.tone().trim());
    entity.setVersion(entity.getVersion() + 1);
    entity.setUpdatedAt(EntityMapper.now());
    mapper.update(entity);
    return toDto(entity);
  }

  @Transactional
  public AgentProfileDto uploadAvatar(MultipartFile file) {
    AgentProfileEntity profile = requireLocked();
    String userId = identity.getCurrentUserId();
    LocalMediaStorage.StoredAvatar stored = mediaStorage.storeAgentAvatar(userId, file);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) mediaStorage.deleteIfPresent(stored.relativePath());
        }
      });
    }
    AgentMediaEntity media = new AgentMediaEntity();
    media.setId(java.util.UUID.randomUUID().toString());
    media.setUserId(userId);
    media.setRelativePath(stored.relativePath());
    media.setContentType(stored.contentType());
    media.setSizeBytes(stored.sizeBytes());
    media.setStatus("active");
    media.setCreatedAt(EntityMapper.now());
    mediaMapper.insert(media);
    if (profile.getAvatarMediaId() != null) {
      mediaMapper.markReplaced(profile.getAvatarMediaId(), userId, EntityMapper.now());
    }
    profile.setAvatarMediaId(media.getId());
    profile.setVersion(profile.getVersion() + 1);
    profile.setUpdatedAt(EntityMapper.now());
    mapper.update(profile);
    return toDto(profile);
  }

  public java.nio.file.Path avatarPath() {
    AgentProfileEntity profile = requireLocked();
    if (profile.getAvatarMediaId() == null) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "avatar not found");
    }
    AgentMediaEntity media = mediaMapper.findById(profile.getAvatarMediaId(), identity.getCurrentUserId());
    if (media == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "avatar not found");
    return mediaStorage.resolveReadable(media.getRelativePath());
  }

  @Transactional
  public AgentProfileDto resetAvatar() {
    AgentProfileEntity profile = requireLocked();
    if (profile.getAvatarMediaId() != null) mediaMapper.markReplaced(profile.getAvatarMediaId(), profile.getUserId(), EntityMapper.now());
    profile.setAvatarMediaId(null);
    profile.setVersion(profile.getVersion() + 1);
    profile.setUpdatedAt(EntityMapper.now());
    mapper.update(profile);
    return toDto(profile);
  }

  private AgentProfileEntity requireLocked() {
    AgentProfileDto profile = getOrCreate();
    return mapper.lockByUserId(profile.userId());
  }

  private AgentProfileDto toDto(AgentProfileEntity entity) {
    String avatarUrl = entity.getAvatarMediaId() == null ? null : "/v1/agent/avatar";
    return new AgentProfileDto(entity.getUserId(), entity.getName(), entity.getTone(), entity.getAvatarMediaId(), avatarUrl, entity.getVersion());
  }
}
