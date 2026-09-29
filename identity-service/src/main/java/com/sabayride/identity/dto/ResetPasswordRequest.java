package com.sabayride.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/password/reset body */
public record ResetPasswordRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
) {}
