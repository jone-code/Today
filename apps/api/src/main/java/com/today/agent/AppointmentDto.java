package com.today.agent;

public record AppointmentDto(String id, String title, String dueAt, String timezone,
    String status, String conversationId) {}
