package com.today.telemetry;

import java.util.Set;

/** The stable event vocabulary used by product metrics and retention queries. */
public final class ProductEventCatalog {
  private ProductEventCatalog() {}

  private static final Set<String> KNOWN = Set.of(
      "auth_completed",
      "today_viewed",
      "checkin_started",
      "checkin_saved",
      "summary_viewed",
      "conversation_started",
      "message_sent",
      "proposal_viewed",
      "proposal_confirmed",
      "proposal_rejected",
      "action_completed",
      "inbox_viewed",
      "inbox_item_opened",
      "inbox_item_read",
      "inbox_item_dismissed",
      "followup_continued",
      "followup_snoozed",
      "followup_stopped",
      "followup_resumed",
      "followup_updated",
      "appointment_cancelled",
      "weekly_review_viewed",
      "memory_updated",
      "memory_context_scope_updated",
      "memory_archived",
      "memory_restored",
      "memory_deleted",
      "voice_recording_started",
      "voice_recording_failed",
      "voice_transcription_requested",
      "voice_transcription_succeeded",
      "voice_transcription_failed",
      "voice_transcription_retried");

  public static boolean isKnown(String eventName) {
    return eventName != null && KNOWN.contains(eventName.trim());
  }
}
