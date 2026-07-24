package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Friendship;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FriendshipRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.service.PresenceService;

@RestController
@RequestMapping("/api/friends")
public class FriendRestController {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public FriendRestController(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            PresenceService presenceService) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    @GetMapping
    public List<Map<String, Object>> friends(Principal principal) {
        String username = principal.getName();
        return friendshipRepository.findAcceptedFor(username).stream()
                .map(friendship -> userSummary(otherUser(friendship, username), friendship))
                .toList();
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests(Principal principal) {
        return friendshipRepository
                .findByAddresseeUsernameAndStatusOrderByCreatedAtDesc(
                        principal.getName(), "PENDING")
                .stream()
                .map(friendship -> userSummary(friendship.getRequester(), friendship))
                .toList();
    }

    @GetMapping("/status/{username}")
    public ResponseEntity<?> status(@PathVariable String username, Principal principal) {
        if (principal.getName().equals(username)) {
            return ResponseEntity.ok(Map.of("status", "SELF"));
        }
        Friendship friendship = friendshipRepository
                .findBetween(principal.getName(), username)
                .orElse(null);
        if (friendship == null) {
            return ResponseEntity.ok(Map.of("status", "NONE"));
        }
        String status = friendship.getStatus();
        if ("PENDING".equals(status)) {
            status = friendship.getRequester().getUsername().equals(principal.getName())
                    ? "PENDING_SENT"
                    : "PENDING_RECEIVED";
        }
        return ResponseEntity.ok(Map.of("id", friendship.getId(), "status", status));
    }

    @PostMapping("/{username}")
    @Transactional
    public ResponseEntity<?> sendRequest(@PathVariable String username, Principal principal) {
        String currentUsername = principal.getName();
        if (currentUsername.equals(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể kết bạn với chính mình"));
        }
        User requester = userRepository.findById(currentUsername).orElse(null);
        User addressee = userRepository.findById(username).orElse(null);
        if (requester == null || addressee == null) {
            return ResponseEntity.notFound().build();
        }
        if (friendshipRepository.findBetween(currentUsername, username).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("message", "Quan hệ kết bạn đã tồn tại"));
        }

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus("PENDING");
        friendshipRepository.save(friendship);
        return ResponseEntity.ok(Map.of(
                "id", friendship.getId(),
                "status", "PENDING_SENT",
                "message", "Đã gửi lời mời kết bạn"));
    }

    @PutMapping("/{id}/accept")
    @Transactional
    public ResponseEntity<?> accept(@PathVariable Integer id, Principal principal) {
        Friendship friendship = friendshipRepository.findById(id).orElse(null);
        if (friendship == null) {
            return ResponseEntity.notFound().build();
        }
        if (!friendship.getAddressee().getUsername().equals(principal.getName())
                || !"PENDING".equals(friendship.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("message", "Không thể chấp nhận lời mời này"));
        }
        friendship.setStatus("ACCEPTED");
        friendship.setRespondedAt(new Date());
        friendshipRepository.save(friendship);
        return ResponseEntity.ok(Map.of("status", "ACCEPTED", "message", "Đã trở thành bạn bè"));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> remove(@PathVariable Integer id, Principal principal) {
        Friendship friendship = friendshipRepository.findById(id).orElse(null);
        if (friendship == null) {
            return ResponseEntity.notFound().build();
        }
        String username = principal.getName();
        boolean participant = friendship.getRequester().getUsername().equals(username)
                || friendship.getAddressee().getUsername().equals(username);
        if (!participant) {
            return ResponseEntity.status(403).body(Map.of("message", "Không có quyền xóa quan hệ này"));
        }
        friendshipRepository.delete(friendship);
        return ResponseEntity.ok(Map.of("status", "NONE", "message", "Đã cập nhật quan hệ bạn bè"));
    }

    private User otherUser(Friendship friendship, String username) {
        return friendship.getRequester().getUsername().equals(username)
                ? friendship.getAddressee()
                : friendship.getRequester();
    }

    private Map<String, Object> userSummary(User user, Friendship friendship) {
        return Map.of(
                "friendshipId", friendship.getId(),
                "username", user.getUsername(),
                "fullname", user.getFullname() == null ? user.getUsername() : user.getFullname(),
                "photo", user.getPhoto() == null ? "" : user.getPhoto(),
                "online", presenceService.isOnline(user),
                "lastSeenAt", user.getLastSeenAt() == null ? "" : user.getLastSeenAt(),
                "status", friendship.getStatus());
    }
}
