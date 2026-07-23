package com.today.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @NotBlank @Email @Size(max = 191) String email,
    @NotBlank @Size(min = 6, max = 72) String password,
    @NotBlank @Size(max = 64) String displayName) {}
