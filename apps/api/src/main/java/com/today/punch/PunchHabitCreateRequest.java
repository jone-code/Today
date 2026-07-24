package com.today.punch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PunchHabitCreateRequest(
    @NotBlank @Size(max = 128) String title,
    @Size(max = 512) String description,
    Boolean enabled) {}
