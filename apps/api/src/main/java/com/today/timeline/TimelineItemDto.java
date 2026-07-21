package com.today.timeline;

import com.today.common.Mood;

public record TimelineItemDto(
    String date,
    String highlight,
    String oneLiner,
    Mood mood,
    String moodLabel,
    String checkinId) {}
