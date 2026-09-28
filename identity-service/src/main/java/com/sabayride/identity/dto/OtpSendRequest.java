package com.sabayride.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
        @NotBlank(message = "Phone number is required") String phone,
        String purpose
) {
    public String resolvedPurpose() {
        return (purpose == null || purpose.isBlank()) ? "PHONE_VERIFICATION" : purpose.trim().toUpperCase();
    }
}
