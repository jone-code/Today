package com.today.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReminderCreateRequest(
    @NotBlank @Size(max = 128) String title,
    @NotBlank @Size(max = 512) String message,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String remindTime,
    @Size(max = 64) String timezone,
    Boolean enabled) {}
