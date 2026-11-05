package com.today.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
    @NotBlank @Pattern(regexp = "issue|idea") String kind,
    @NotBlank @Size(max = 1000) String body) {}
