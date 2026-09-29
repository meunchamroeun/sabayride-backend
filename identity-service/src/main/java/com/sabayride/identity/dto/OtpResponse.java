package com.sabayride.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpResponse(
        boolean success,
        String message,
        String code
) {
    public static OtpResponse ok(String message) {
        return new OtpResponse(true, message, null);
    }

    public static OtpResponse ok(String message, String code) {
        return new OtpResponse(true, message, code);
    }

    public static OtpResponse failed(String message) {
        return new OtpResponse(false, message, null);
    }
}
