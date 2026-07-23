package com.today.punch;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PunchToggleRequest(
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date,
    @Size(max = 512) String note) {}
