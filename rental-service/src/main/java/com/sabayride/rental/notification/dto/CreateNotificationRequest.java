package com.sabayride.rental.notification.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateNotificationRequest(
    UUID userId,
    @NotBlank String type,
    @NotBlank String title,
    String body,
    UUID bookingId
) {}
