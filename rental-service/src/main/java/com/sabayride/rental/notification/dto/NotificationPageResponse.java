package com.sabayride.rental.notification.dto;

import java.util.List;

public record NotificationPageResponse(
    List<NotificationResponse> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    long unreadCount
) {}
