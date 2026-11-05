package com.today.agent;

/** Untrusted model output; validated before becoming a user-visible proposal. */
public record ConversationProposal(String kind, String category, String text, String reason,
    Integer suggestedDays, String title, String note, String dueDate, String description,
    String remindTime, String scheduledLocal, String checkinDate, String targetId, Boolean clearDueDate,
    String punchDate, Boolean habitEnabled, String reminderMessage, String reminderTimezone,
    Boolean reminderEnabled) {
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays) {
    this(kind, category, text, reason, suggestedDays, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime, null, null, null, null, null, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime, String scheduledLocal) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime, scheduledLocal, null, null, null, null, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime, String scheduledLocal,
      String checkinDate) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime,
        scheduledLocal, checkinDate, null, null, null, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime, String scheduledLocal,
      String checkinDate, String targetId, Boolean clearDueDate) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime,
        scheduledLocal, checkinDate, targetId, clearDueDate, null, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime, String scheduledLocal,
      String checkinDate, String targetId, Boolean clearDueDate, String punchDate) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime,
        scheduledLocal, checkinDate, targetId, clearDueDate, punchDate, null, null, null, null);
  }
  public ConversationProposal(String kind, String category, String text, String reason, Integer suggestedDays,
      String title, String note, String dueDate, String description, String remindTime, String scheduledLocal,
      String checkinDate, String targetId, Boolean clearDueDate, String punchDate, Boolean habitEnabled) {
    this(kind, category, text, reason, suggestedDays, title, note, dueDate, description, remindTime,
        scheduledLocal, checkinDate, targetId, clearDueDate, punchDate, habitEnabled, null, null, null);
  }
}
