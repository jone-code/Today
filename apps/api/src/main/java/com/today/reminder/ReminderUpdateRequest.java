package com.today.reminder;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReminderUpdateRequest(
    @Size(max = 128) String title,
    @Size(max = 512) String message,
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String remindTime,
    @Size(max = 64) String timezone,
    Boolean enabled) {}
