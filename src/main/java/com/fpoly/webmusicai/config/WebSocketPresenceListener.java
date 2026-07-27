package com.fpoly.webmusicai.config;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.fpoly.webmusicai.service.PresenceService;

@Component
public class WebSocketPresenceListener {

    private final PresenceService presenceService;
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    public WebSocketPresenceListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void connected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user != null && accessor.getSessionId() != null) {
            sessionUsers.put(accessor.getSessionId(), user.getName());
            presenceService.websocketConnected(user.getName());
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String username = sessionUsers.remove(event.getSessionId());
        if (username != null) {
            presenceService.websocketDisconnected(username);
        }
    }
}
