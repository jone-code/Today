package com.today.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequest(
    @NotBlank @Email @Size(max = 191) String email,
    @NotBlank @Size(max = 72) String password) {}
