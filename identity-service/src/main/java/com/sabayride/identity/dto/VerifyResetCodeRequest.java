package com.sabayride.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /auth/password/verify-code body
 * Supports phone number or email all-in-one.
 */
public record VerifyResetCodeRequest(
        String phone,
        String email,
        String identifier,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits") String code
) {
    @JsonIgnore
    public String resolvedIdentifier() {
        if (identifier != null && !identifier.isBlank()) return identifier.trim();
        if (email != null && !email.isBlank()) return email.trim();
        if (phone != null && !phone.isBlank()) return phone.trim();
        return "";
    }
}
