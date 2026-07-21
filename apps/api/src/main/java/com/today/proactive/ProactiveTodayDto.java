package com.today.proactive;

import com.today.common.AiProvider;
import java.util.List;

public record ProactiveTodayDto(
    String date, List<ProactivePromptDto> prompts, AiProvider provider) {}
