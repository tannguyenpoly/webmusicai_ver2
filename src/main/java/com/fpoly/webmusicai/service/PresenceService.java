package com.fpoly.webmusicai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class PresenceService {

    private final UserRepository userRepository;
    private final Duration onlineWindow;
    private final Map<String, Instant> heartbeats = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPersisted = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> websocketConnections = new ConcurrentHashMap<>();

    public PresenceService(
            UserRepository userRepository,
            @Value("${presence.online-window-seconds:90}") long onlineWindowSeconds) {
        this.userRepository = userRepository;
        this.onlineWindow = Duration.ofSeconds(onlineWindowSeconds);
    }

    @Transactional
    public void heartbeat(String username) {
        if (username == null || "anonymousUser".equals(username)) {
            return;
        }
        Instant now = Instant.now();
        heartbeats.put(username, now);
        Instant previous = lastPersisted.get(username);
        if (previous == null || Duration.between(previous, now).getSeconds() >= 30) {
            userRepository.touchLastSeen(username, Date.from(now));
            lastPersisted.put(username, now);
        }
    }

    public void websocketConnected(String username) {
        if (username == null) {
            return;
        }
        websocketConnections.computeIfAbsent(username, ignored -> new AtomicInteger())
                .incrementAndGet();
        heartbeat(username);
    }

    @Transactional
    public void offline(String username) {
        if (username == null || "anonymousUser".equals(username)) {
            return;
        }
        websocketConnections.remove(username);
        heartbeats.remove(username);
        lastPersisted.remove(username);
        userRepository.touchLastSeen(username, new Date());
    }

    @Transactional
    public void websocketDisconnected(String username) {
        if (username == null) {
            return;
        }
        websocketConnections.computeIfPresent(username, (key, count) ->
                count.decrementAndGet() <= 0 ? null : count);
        Instant now = Instant.now();
        if (!websocketConnections.containsKey(username)) {
            heartbeats.remove(username);
        }
        userRepository.touchLastSeen(username, Date.from(now));
        lastPersisted.put(username, now);
    }

    public boolean isOnline(User user) {
        if (user == null) {
            return false;
        }
        AtomicInteger connections = websocketConnections.get(user.getUsername());
        if (connections != null && connections.get() > 0) {
            return true;
        }
        Instant heartbeat = heartbeats.get(user.getUsername());
        return heartbeat != null && heartbeat.isAfter(Instant.now().minus(onlineWindow));
    }
}
