package com.today.checkin;

public record CheckinMemoryCandidateDto(
    String id, String checkinId, String category, String text, String status,
    String createdAt, String confirmedAt) {}
