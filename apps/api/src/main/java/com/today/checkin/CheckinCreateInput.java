package com.today.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckinCreateInput(
    @NotBlank @Size(max = 2000) String rawText,
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {}
