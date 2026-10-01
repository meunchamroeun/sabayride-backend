package com.sabayride.rental.notification.dto;

import com.sabayride.rental.notification.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID userId,
    String type,
    String title,
    String body,
    UUID bookingId,
    boolean read,
    Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getUserId(),
            n.getType(),
            n.getTitle(),
            n.getBody(),
            n.getBookingId(),
            n.isRead(),
            n.getCreatedAt()
        );
    }
}
