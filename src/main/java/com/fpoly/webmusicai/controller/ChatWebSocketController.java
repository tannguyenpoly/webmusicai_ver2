package com.fpoly.webmusicai.controller;

import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.fpoly.webmusicai.dto.ChatMessageRequest;
import com.fpoly.webmusicai.dto.ChatMessageResponse;
import com.fpoly.webmusicai.service.ChatMessageService;

import jakarta.validation.Valid;

@Controller
public class ChatWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageService chatMessageService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            return;
        }

        String senderUsername = principal.getName();
        ChatMessageResponse savedMessage = chatMessageService.send(senderUsername, request);

        messagingTemplate.convertAndSendToUser(
            request.recipientUsername(), "/queue/messages", savedMessage);
        messagingTemplate.convertAndSendToUser(
            senderUsername, "/queue/messages", savedMessage);
    }
}
