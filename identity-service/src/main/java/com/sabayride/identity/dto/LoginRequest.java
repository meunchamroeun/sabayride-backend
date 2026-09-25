package com.sabayride.identity.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/login body (contract: identifier = phone or email, + password). */
public record LoginRequest(
        @NotBlank String identifier,
        @NotBlank String password
) {
}
