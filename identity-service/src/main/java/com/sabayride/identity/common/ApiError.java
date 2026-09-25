package com.sabayride.identity.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Single error shape every endpoint returns. Matches the API contract's Error schema. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        String field,
        Instant timestamp,
        String traceId
) {
    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field, Instant.now(), null);
    }
}
