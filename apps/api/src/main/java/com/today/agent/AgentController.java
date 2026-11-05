package com.today.agent;

import com.today.common.RateLimitService;
import com.today.common.CreateIdempotencyService;
import com.today.identity.IdentityService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/agent")
public class AgentController {
  private final AgentProfileService profiles;
  private final ConversationService conversations;
  private final ProposalService proposals;
  private final FollowupService followups;
  private final AppointmentService appointments;
  private final RateLimitService rateLimits;
  private final IdentityService identity;
  private final CreateIdempotencyService requests;
  @Value("${today.rate-limit.avatar-per-day:20}") private int avatarPerDay = 20;

  public AgentController(AgentProfileService profiles, ConversationService conversations, ProposalService proposals, FollowupService followups, AppointmentService appointments, RateLimitService rateLimits, IdentityService identity, CreateIdempotencyService requests) {
    this.profiles = profiles;
    this.conversations = conversations;
    this.proposals = proposals;
    this.followups = followups;
    this.appointments = appointments;
    this.rateLimits = rateLimits;
    this.identity = identity;
    this.requests = requests;
  }

  @GetMapping("/profile")
  public AgentProfileDto profile() { return profiles.getOrCreate(); }

  @PutMapping("/profile")
  public AgentProfileDto updateProfile(@Valid @RequestBody AgentProfileUpdateRequest input) { return profiles.update(input); }

  @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AgentProfileDto uploadAvatar(@RequestPart("avatar") MultipartFile avatar,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    Object request = key == null ? null : new AvatarUpload(avatar.getContentType(),
        avatar.getOriginalFilename(), sha256(avatar));
    return requests.create("avatar", key, request, AgentProfileDto.class, () -> {
      rateLimits.check("avatar_day", identity.getCurrentUserId(), avatarPerDay, Duration.ofDays(1));
      return profiles.uploadAvatar(avatar);
    });
  }

  private static String sha256(MultipartFile avatar) {
    try (InputStream input = avatar.getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = new byte[8192];
      int count;
      while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unreadable avatar", e);
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  private record AvatarUpload(String contentType, String fileName, String sha256) {}

  @DeleteMapping("/avatar")
  public AgentProfileDto resetAvatar() { return profiles.resetAvatar(); }

  @GetMapping("/avatar")
  public ResponseEntity<FileSystemResource> avatar() {
    java.nio.file.Path path = profiles.avatarPath();
    MediaType type = MediaType.APPLICATION_OCTET_STREAM;
    try { type = MediaType.parseMediaType(java.nio.file.Files.probeContentType(path)); } catch (Exception ignored) {}
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").contentType(type).body(new FileSystemResource(path));
  }

  @GetMapping("/conversations")
  public List<ConversationDto> list(@RequestParam(defaultValue = "20") int limit) { return conversations.list(limit); }

  @PostMapping("/conversations")
  public ConversationDto create(@Valid @RequestBody ConversationCreateRequest input) { return conversations.create(input); }

  @GetMapping("/conversations/{id}")
  public ConversationDto get(@PathVariable String id, @RequestParam(defaultValue = "50") int limit) { return conversations.get(id, limit); }

  @GetMapping("/conversations/{id}/messages")
  public ConversationMessagePage messages(@PathVariable String id,
      @RequestParam(required = false) String before,
      @RequestParam(required = false) String beforeId,
      @RequestParam(defaultValue = "50") int limit) {
    return conversations.messages(id, before, beforeId, limit);
  }

  @PostMapping("/conversations/{id}/messages")
  public ConversationMessageResult send(@PathVariable String id, @Valid @RequestBody ConversationMessageRequest input) { return conversations.send(id, input); }

  @PostMapping("/conversations/{id}/messages/{messageId}/retry")
  public ConversationMessageResult retry(@PathVariable String id, @PathVariable String messageId) {
    return conversations.retry(id, messageId);
  }

  @DeleteMapping("/conversations/{id}")
  public void delete(@PathVariable String id) { conversations.delete(id); }

  @GetMapping("/proposals")
  public List<AgentProposalDto> proposals() { return proposals.pending(); }

  @GetMapping("/actions")
  public List<AgentActionDto> actions(@RequestParam(defaultValue = "30") int limit) { return proposals.actions(limit); }

  @PostMapping("/actions/{proposalId}/undo")
  public AgentActionDto undoAction(@PathVariable String proposalId) { return proposals.undoAction(proposalId); }

  @GetMapping("/appointments")
  public List<AppointmentDto> appointments() { return appointments.upcoming(); }

  @PostMapping("/appointments/{id}/cancel")
  public void cancelAppointment(@PathVariable String id) { appointments.cancel(id); }

  @GetMapping("/inbox")
  public List<InboxItemDto> inbox(@RequestParam(defaultValue = "50") int limit) { return followups.inbox(limit); }

  @GetMapping("/inbox/unread-count")
  public InboxUnreadCountDto unreadCount() { return new InboxUnreadCountDto(followups.unreadCount()); }

  public record InboxUnreadCountDto(int count) {}

  @GetMapping("/followups/preferences")
  public FollowupPreferenceDto followupPreferences() { return followups.preferences(); }

  @PatchMapping("/followups/preferences")
  public FollowupPreferenceDto updateFollowupPreferences(@Valid @RequestBody FollowupPreferenceUpdateRequest input) {
    return followups.updatePreferences(input);
  }

  @PostMapping("/proposals/{id}/confirm")
  public AgentProposalDto confirm(@PathVariable String id) { return proposals.confirm(id); }

  @PatchMapping("/proposals/{id}/checkin-preview")
  public AgentProposalDto updateCheckinPreview(@PathVariable String id,
      @Valid @RequestBody CheckinPreviewUpdateRequest input) { return proposals.updateCheckinPreview(id, input); }

  @PostMapping("/proposals/{id}/reject")
  public AgentProposalDto reject(@PathVariable String id) { return proposals.reject(id); }

  @PostMapping("/inbox/{id}/read")
  public InboxItemDto readInbox(@PathVariable String id) { return followups.read(id); }

  @PostMapping("/inbox/{id}/dismiss")
  public InboxItemDto dismissInbox(@PathVariable String id) { return followups.dismiss(id); }

  @PostMapping("/inbox/{id}/snooze")
  public InboxItemDto snoozeInbox(@PathVariable String id, @Valid @RequestBody InboxSnoozeRequest input) {
    if ((input.date() == null) == (input.until() == null)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provide either date or until");
    }
    if (input.date() != null) return followups.snoozeOnDate(id, input.date());
    return followups.snooze(id, input.until());
  }

  @PostMapping("/followups/topics/{id}/stop")
  public void stopFollowup(@PathVariable String id) { followups.stopTopic(id); }

  @GetMapping("/followups/topics")
  public List<FollowupTopicDto> followupTopics(@RequestParam(defaultValue = "50") int limit) {
    return followups.topics(limit);
  }

  @PatchMapping("/followups/topics/{id}")
  public FollowupTopicDto updateFollowupTopic(@PathVariable String id,
      @Valid @RequestBody FollowupTopicUpdateRequest input) {
    return followups.updateTopic(id, input);
  }

  @PostMapping("/followups/topics/{id}/resume")
  public void resumeFollowup(@PathVariable String id) { followups.resumeTopic(id); }

  @PostMapping("/inbox/{id}/continue")
  public InboxItemDto continueInbox(@PathVariable String id) { return followups.continueConversation(id); }
}
