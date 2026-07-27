package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.webmusicai.entity.Notification;
import com.fpoly.webmusicai.repository.NotificationRepository;

@RestController
@RequestMapping("/api/notifications")
public class NotificationRestController {
    private final NotificationRepository notificationRepository;

    public NotificationRestController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            Principal principal,
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return notificationRepository
                .findByUserUsernameOrderByCreatedAtDesc(
                        principal.getName(), PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toMap)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Principal principal) {
        return Map.of("count",
                notificationRepository.countByUserUsernameAndReadFalse(principal.getName()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Integer id, Principal principal) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        if (!notification.getUser().getUsername().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Bạn không có quyền sửa thông báo này"));
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(Map.of("id", id, "read", true));
    }

    @PutMapping("/read-all")
    public Map<String, Object> markAllRead(Principal principal) {
        List<Notification> unread =
                notificationRepository.findByUserUsernameAndReadFalse(principal.getName());
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
        return Map.of("updated", unread.size());
    }

    private Map<String, Object> toMap(Notification notification) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", notification.getId());
        result.put("type", notification.getType());
        result.put("content", notification.getContent());
        result.put("read", Boolean.TRUE.equals(notification.getRead()));
        result.put("refId", notification.getRefId());
        result.put("createdAt", notification.getCreatedAt());
        return result;
    }
}
