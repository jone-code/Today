package com.today.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.persistence.FollowupPreferenceEntity;
import com.today.persistence.FollowupTopicEntity;
import com.today.persistence.InboxItemEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FollowupServiceTest {
  private final FollowupMapper mapper = mock(FollowupMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final FollowupService service = new FollowupService(mapper, identity, mock(AppointmentMapper.class));

  @Test
  void unreadCountWakesDueSnoozesForTheCurrentAccount() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.countUnread("u1")).thenReturn(3);

    assertEquals(3, service.unreadCount());

    verify(mapper).wakeSnoozed(eq("u1"), any(Instant.class));
    verify(mapper).countUnread("u1");
  }

  @Test
  void stoppingTopicAlsoDismissesPendingMessages() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.stopTopic(eq("topic-1"), eq("u1"), any(Instant.class))).thenReturn(1);

    service.stopTopic("topic-1");

    verify(mapper).dismissTopicInbox("topic-1", "u1");
  }

  @Test
  void listsOnlyTopicsReturnedForCurrentUser() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    FollowupTopicEntity topic = new FollowupTopicEntity();
    topic.setId("t1"); topic.setReason("面试结果"); topic.setState("authorized");
    topic.setNextEligibleAt(Instant.parse("2026-11-10T00:00:00Z"));
    topic.setCreatedAt(Instant.parse("2026-11-03T00:00:00Z"));
    when(mapper.listUserTopics("u1", 50)).thenReturn(List.of(topic));

    assertEquals("面试结果", service.topics(50).get(0).reason());
    verify(mapper).listUserTopics("u1", 50);
  }

  @Test
  void explicitResumeResetsEligibilityWithoutImmediatelyDispatching() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.resumeTopic(eq("t1"), eq("u1"), any(Instant.class), any(Instant.class)))
        .thenAnswer(invocation -> {
          Instant now = invocation.getArgument(2);
          Instant eligible = invocation.getArgument(3);
          assertEquals(now.plusSeconds(86400), eligible);
          return 1;
        });

    service.resumeTopic("t1");
  }

  @Test
  void resumeRejectsTopicsNotOwnedOrWithoutActiveSource() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    assertThrows(ResponseStatusException.class, () -> service.resumeTopic("missing"));
  }

  @Test
  void dispatchRespectsUsersLocalDayAndWeekLimits() {
    Instant now = Instant.parse("2026-11-03T17:30:00Z"); // Wednesday 01:30 in Shanghai
    FollowupTopicEntity topic = dueTopic(now);
    when(mapper.listDueTopics(now)).thenReturn(List.of(topic));
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockPreference("u1")).thenReturn(preference());
    Instant day = Instant.parse("2026-11-03T16:00:00Z");
    Instant week = Instant.parse("2026-11-01T16:00:00Z");
    when(mapper.countDeliveredSinceLocked("u1", day)).thenReturn(1, 0);
    when(mapper.countDeliveredSinceLocked("u1", week)).thenReturn(3);

    assertEquals(0, service.dispatchDue(now));
    assertEquals(now.plusSeconds(3600), topic.getNextEligibleAt());
    verify(mapper, never()).insertInbox(any());

    topic.setNextEligibleAt(now);
    assertEquals(0, service.dispatchDue(now));
    assertEquals(now.plusSeconds(3600), topic.getNextEligibleAt());
    verify(mapper).countDeliveredSinceLocked("u1", week);
    verify(mapper, never()).insertInbox(any());
  }

  @Test
  void unreadFollowupIsNotRepeatedEvenWhenDue() {
    Instant now = Instant.parse("2026-11-03T09:00:00Z");
    FollowupTopicEntity topic = dueTopic(now);
    when(mapper.listDueTopics(now)).thenReturn(List.of(topic));
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockPreference("u1")).thenReturn(preference());
    InboxItemEntity previous = new InboxItemEntity();
    previous.setState("unread");
    when(mapper.latestForTopic("u1", "t1")).thenReturn(previous);

    assertEquals(0, service.dispatchDue(now));
    assertEquals(now.plusSeconds(86400), topic.getNextEligibleAt());
    verify(mapper).updateTopic(topic);
    verify(mapper, never()).insertInbox(any());
  }

  @Test
  void disabledFollowupIsDeferredWithoutDelivery() {
    Instant now = Instant.parse("2026-11-03T09:00:00Z");
    FollowupTopicEntity topic = dueTopic(now);
    FollowupPreferenceEntity disabled = preference();
    disabled.setEnabled(false);
    when(mapper.listDueTopics(now)).thenReturn(List.of(topic));
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockPreference("u1")).thenReturn(disabled);

    assertEquals(0, service.dispatchDue(now));
    assertEquals(now.plusSeconds(86400), topic.getNextEligibleAt());
    verify(mapper).updateTopic(topic);
    verify(mapper, never()).insertInbox(any());
  }

  @Test
  void deliveredTopicWaitsSevenDaysAndCountsOneUnansweredMessage() {
    Instant now = Instant.parse("2026-11-03T09:00:00Z");
    FollowupTopicEntity topic = dueTopic(now);
    when(mapper.listDueTopics(now)).thenReturn(List.of(topic));
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockPreference("u1")).thenReturn(preference());
    when(mapper.insertInbox(any())).thenReturn(1);

    assertEquals(1, service.dispatchDue(now));
    assertEquals(now.plusSeconds(7 * 86400), topic.getNextEligibleAt());
    assertEquals(1, topic.getUnansweredCount());
    assertEquals(now, topic.getLastDeliveredAt());
    verify(mapper).updateTopic(topic);
  }

  @Test
  void twoUnansweredDeliveriesPauseTheTopicBeforeAnotherMessage() {
    Instant now = Instant.parse("2026-11-03T09:00:00Z");
    FollowupTopicEntity topic = dueTopic(now);
    topic.setUnansweredCount(2);
    when(mapper.listDueTopics(now)).thenReturn(List.of(topic));
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockPreference("u1")).thenReturn(preference());
    InboxItemEntity previous = new InboxItemEntity();
    previous.setState("unread");
    when(mapper.latestForTopic("u1", "t1")).thenReturn(previous);

    assertEquals(0, service.dispatchDue(now));
    assertEquals("paused", topic.getState());
    verify(mapper).updateTopic(topic);
    verify(mapper, never()).latestForTopic("u1", "t1");
    verify(mapper, never()).insertInbox(any());
  }

  @Test
  void continuingAutoPausedInboxResumesWithoutReactivatingManuallyStoppedTopics() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    InboxItemEntity item = inboxItem();
    FollowupTopicEntity topic = dueTopic(Instant.parse("2026-11-03T09:00:00Z"));
    topic.setSourceConversationId("c1");
    topic.setState("paused");
    topic.setUnansweredCount(2);
    when(mapper.findInbox("i1", "u1")).thenReturn(item);
    when(mapper.lockTopic("t1", "u1")).thenReturn(topic);
    when(mapper.lockInbox("i1", "u1")).thenReturn(item);
    when(mapper.resumeAutoPausedFromInbox(eq("t1"), eq("u1"), any(), any())).thenReturn(1);
    when(mapper.updateInboxState(eq("i1"), eq("u1"), eq("handled"), any(), eq(null)))
        .thenAnswer(invocation -> { item.setState("handled"); return 1; });
    when(mapper.findTopic("t1", "u1")).thenReturn(topic);

    assertEquals("c1", service.continueConversation("i1").conversationId());
    verify(mapper).resumeAutoPausedFromInbox(eq("t1"), eq("u1"), any(), any());

    item.setState("dismissed");
    assertThrows(ResponseStatusException.class, () -> service.continueConversation("i1"));
  }

  @Test
  void snoozingFollowupAcknowledgesTheTopic() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    InboxItemEntity item = inboxItem();
    when(mapper.findInbox("i1", "u1")).thenReturn(item);
    when(mapper.updateInboxState(eq("i1"), eq("u1"), eq("snoozed"), eq(null), any())).thenReturn(1);

    service.snooze("i1", Instant.now().plusSeconds(3600));

    verify(mapper).acknowledgeSnoozedTopic(eq("t1"), eq("u1"), any());
  }

  @Test
  void snoozeRejectsPastDatesWithoutWriting() {
    assertThrows(ResponseStatusException.class, () -> service.snooze("inbox-1", Instant.now().minusSeconds(60)));
    verifyNoInteractions(mapper);
  }

  @Test
  void snoozeDateUsesTheUsersTimezoneInsteadOfThePhonesTimezone() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    FollowupPreferenceEntity pref = preference();
    pref.setTimezone("Pacific/Honolulu");
    when(mapper.lockPreference("u1")).thenReturn(pref);
    InboxItemEntity item = inboxItem();
    when(mapper.findInbox("i1", "u1")).thenReturn(item);
    LocalDate selected = LocalDate.now(ZoneId.of(pref.getTimezone())).plusDays(1);
    Instant expected = selected.atTime(9, 0).atZone(ZoneId.of(pref.getTimezone())).toInstant();
    when(mapper.updateInboxState("i1", "u1", "snoozed", null, expected))
        .thenAnswer(invocation -> { item.setState("snoozed"); item.setSnoozedUntil(expected); return 1; });

    var result = service.snoozeOnDate("i1", selected.toString());

    assertEquals(expected.toString(), result.snoozedUntil());
    verify(mapper).updateInboxState("i1", "u1", "snoozed", null, expected);
    verify(mapper).acknowledgeSnoozedTopic(eq("t1"), eq("u1"), any(Instant.class));
  }

  @Test
  void snoozeDateRejectsInvalidAndOutOfRangeDates() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(mapper.lockPreference("u1")).thenReturn(preference());
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    for (String invalid : List.of("2026-02-30", today.toString(), today.plusDays(31).toString())) {
      ResponseStatusException failure = assertThrows(ResponseStatusException.class,
          () -> service.snoozeOnDate("i1", invalid));
      assertEquals(400, failure.getStatusCode().value());
    }
    verify(mapper, never()).updateInboxState(any(), any(), any(), any(), any());
  }

  @Test
  void updatePreferencesPersistsUserChoice() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    FollowupPreferenceEntity pref = new FollowupPreferenceEntity();
    pref.setUserId("u1"); pref.setEnabled(true); pref.setMaxPerDay(1); pref.setMaxPerWeek(3);
    pref.setTimezone("Asia/Shanghai");
    when(mapper.lockPreference("u1")).thenReturn(pref);

    var updated = service.updatePreferences(new FollowupPreferenceUpdateRequest(false, 0, 2, "Asia/Shanghai"));

    assertEquals(false, updated.enabled());
    assertEquals(2, updated.maxPerWeek());
    verify(mapper).updatePreference(pref);
  }

  private static FollowupTopicEntity dueTopic(Instant now) {
    FollowupTopicEntity topic = new FollowupTopicEntity();
    topic.setId("t1"); topic.setUserId("u1"); topic.setReason("旅行计划");
    topic.setState("authorized"); topic.setNextEligibleAt(now);
    return topic;
  }

  private static FollowupPreferenceEntity preference() {
    FollowupPreferenceEntity pref = new FollowupPreferenceEntity();
    pref.setUserId("u1"); pref.setEnabled(true); pref.setMaxPerDay(1);
    pref.setMaxPerWeek(3); pref.setTimezone("Asia/Shanghai");
    return pref;
  }

  private static InboxItemEntity inboxItem() {
    InboxItemEntity item = new InboxItemEntity();
    item.setId("i1"); item.setUserId("u1"); item.setSourceKind("followup_topic");
    item.setSourceId("t1"); item.setState("unread"); item.setKind("followup");
    item.setBody("想继续聊吗？");
    item.setScheduledAt(Instant.parse("2026-11-03T09:00:00Z"));
    item.setCreatedAt(Instant.parse("2026-11-03T09:00:00Z"));
    return item;
  }
}
