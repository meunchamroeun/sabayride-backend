package com.sabayride.identity.dto;

/** Contract AuthTokens: accessToken, refreshToken, expiresIn required; tokenType defaults Bearer. */
public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        MeResponse user
) {
    public static AuthTokensResponse bearer(String access, String refresh, long expiresInSeconds, MeResponse user) {
        return new AuthTokensResponse(access, refresh, "Bearer", expiresInSeconds, user);
    }
}
