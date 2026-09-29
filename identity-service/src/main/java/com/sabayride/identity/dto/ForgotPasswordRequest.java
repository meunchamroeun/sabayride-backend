package com.sabayride.identity.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /auth/password/forgot body */
public record ForgotPasswordRequest(
        @NotBlank String phone
) {}
