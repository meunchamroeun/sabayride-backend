package com.sabayride.rental.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabayride.rental.notification.dto.CreateNotificationRequest;
import com.sabayride.rental.notification.dto.NotificationPageResponse;
import com.sabayride.rental.notification.dto.NotificationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/me/notifications", "/me/notifications"})
public class NotificationController {

    private final NotificationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Fallback user if token not supplied (Chamroeun Meun account in identity.users)
    private static final UUID FALLBACK_USER = UUID.fromString("9c5b502c-5be7-4fe1-9641-9e8c462046b6");

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-Id", required = false) UUID headerCustomerId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId,
            @RequestParam(value = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        UUID userId = resolveUserId(authHeader, headerCustomerId, headerUserId);
        return ResponseEntity.ok(service.listNotifications(userId, unreadOnly, page, size));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @PathVariable UUID notificationId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-Id", required = false) UUID headerCustomerId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId) {

        UUID userId = resolveUserId(authHeader, headerCustomerId, headerUserId);
        service.markAsRead(notificationId, userId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-Id", required = false) UUID headerCustomerId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId) {

        UUID userId = resolveUserId(authHeader, headerCustomerId, headerUserId);
        service.markAllAsRead(userId);
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-Id", required = false) UUID headerCustomerId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId) {

        UUID userId = resolveUserId(authHeader, headerCustomerId, headerUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createNotification(req, userId));
    }

    private UUID resolveUserId(String authHeader, UUID headerCustomerId, UUID headerUserId) {
        if (headerUserId != null) return headerUserId;
        if (headerCustomerId != null) return headerCustomerId;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode node = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
                    if (node.has("sub")) {
                        return UUID.fromString(node.get("sub").asText());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return FALLBACK_USER;
    }
}
