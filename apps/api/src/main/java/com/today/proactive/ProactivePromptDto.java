package com.today.proactive;

import com.today.common.PromptSource;

public record ProactivePromptDto(
    String id, String text, String relatedDate, PromptSource source) {}
