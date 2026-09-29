package com.sabayride.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * POST /auth/password/forgot body
 * Supports phone number or email all-in-one.
 */
public record ForgotPasswordRequest(
        String phone,
        String email,
        String identifier
) {
    @JsonIgnore
    public String resolvedIdentifier() {
        if (identifier != null && !identifier.isBlank()) return identifier.trim();
        if (email != null && !email.isBlank()) return email.trim();
        if (phone != null && !phone.isBlank()) return phone.trim();
        return "";
    }
}
