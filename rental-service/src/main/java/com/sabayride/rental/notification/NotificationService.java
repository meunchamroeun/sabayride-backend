package com.sabayride.rental.notification;

import com.sabayride.rental.notification.dto.CreateNotificationRequest;
import com.sabayride.rental.notification.dto.NotificationPageResponse;
import com.sabayride.rental.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<Notification> p = unreadOnly
            ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
            : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        long unreadCount = repository.countByUserIdAndReadFalse(userId);
        List<NotificationResponse> list = p.getContent().stream().map(NotificationResponse::from).toList();

        return new NotificationPageResponse(
            list,
            p.getTotalElements(),
            p.getTotalPages(),
            p.getNumber(),
            p.getSize(),
            unreadCount
        );
    }

    public void markAsRead(UUID id, UUID userId) {
        repository.markAsRead(id, userId);
    }

    public void markAllAsRead(UUID userId) {
        repository.markAllAsRead(userId);
    }

    public NotificationResponse createNotification(CreateNotificationRequest req, UUID defaultUserId) {
        UUID targetUser = req.userId() != null ? req.userId() : defaultUserId;
        Notification n = new Notification(
            targetUser,
            req.type(),
            req.title(),
            req.body(),
            req.bookingId()
        );
        Notification saved = repository.save(n);
        return NotificationResponse.from(saved);
    }
}
