package com.sabayride.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/register body (contract RegisterRequest: fullName, phone, password required). */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank String phone,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8) String password,
        String preferredLanguage
) {
}
