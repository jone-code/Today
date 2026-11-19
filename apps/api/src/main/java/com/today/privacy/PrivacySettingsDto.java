package com.today.privacy;

public record PrivacySettingsDto(String userId, boolean chatMemoryEnabled,
    String voiceRetention, boolean followupEnabled, int version, String updatedAt) {}
