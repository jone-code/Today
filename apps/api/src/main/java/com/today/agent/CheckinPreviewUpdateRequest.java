package com.today.agent;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckinPreviewUpdateRequest(
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date,
    @Size(max = 2000) String text) {}
