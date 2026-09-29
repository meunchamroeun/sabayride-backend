package com.sabayride.identity.dto;

import java.util.Map;

/** POST /auth/password/verify-code response */
public record ResetCodeVerifiedResponse(
        String resetToken,
        int expiresIn
) {
    public static ResetCodeVerifiedResponse of(String resetToken, int expiresIn) {
        return new ResetCodeVerifiedResponse(resetToken, expiresIn);
    }
}
