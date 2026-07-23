package com.today.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TodoCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 1000) String note,
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String dueDate) {}
