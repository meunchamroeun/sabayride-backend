package com.sabayride.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /auth/password/verify-code body */
public record VerifyResetCodeRequest(
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits") String code
) {}
