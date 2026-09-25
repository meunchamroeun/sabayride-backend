package com.sabayride.identity.dto;

import java.util.List;
import java.util.UUID;

/** Contract Me schema (subset the frontend reads: id, fullName, phone, email, roles, phoneVerified). */
public record MeResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        List<String> roles,
        boolean phoneVerified,
        boolean emailVerified,
        String status
) {
}
