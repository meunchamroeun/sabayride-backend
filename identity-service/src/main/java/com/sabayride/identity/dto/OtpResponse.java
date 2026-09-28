package com.sabayride.identity.dto;

public record OtpResponse(
        boolean success,
        String message
) {
    public static OtpResponse ok(String message) {
        return new OtpResponse(true, message);
    }

    public static OtpResponse failed(String message) {
        return new OtpResponse(false, message);
    }
}
