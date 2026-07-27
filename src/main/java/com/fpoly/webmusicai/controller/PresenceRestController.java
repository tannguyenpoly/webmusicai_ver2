package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.service.PresenceService;

@RestController
@RequestMapping("/api/presence")
public class PresenceRestController {

    private final PresenceService presenceService;
    private final UserRepository userRepository;

    public PresenceRestController(PresenceService presenceService, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(Principal principal) {
        presenceService.heartbeat(principal.getName());
        return ResponseEntity.ok(Map.of("online", true));
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> status(@PathVariable String username) {
        User user = userRepository.findById(username).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "username", username,
                "online", presenceService.isOnline(user),
                "lastSeenAt", user.getLastSeenAt() == null ? "" : user.getLastSeenAt()));
    }
}
