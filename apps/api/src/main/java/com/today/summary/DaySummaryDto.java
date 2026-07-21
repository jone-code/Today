package com.today.summary;

import com.today.common.AiProvider;
import com.today.common.Mood;
import java.util.List;

public record DaySummaryDto(
    String checkinId,
    String date,
    List<String> completed,
    Mood mood,
    String moodLabel,
    List<String> keywords,
    String oneLiner,
    String highlight,
    AiProvider provider,
    String createdAt) {}
