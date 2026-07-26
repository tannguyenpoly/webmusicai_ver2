package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.fpoly.webmusicai.entity.ChatMessage;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.ChatMessageRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@Controller
public class ChatWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepo;

    @Autowired
    private UserRepository userRepo;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null) {
            return;
        }

        String senderUsername = principal.getName();
        String recipientUsername = chatMessage.getRecipient() != null ? chatMessage.getRecipient().getUsername() : null;

        if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
            return;
        }

        Optional<User> senderOpt = userRepo.findById(senderUsername);
        Optional<User> recipientOpt = userRepo.findById(recipientUsername);

        if (senderOpt.isPresent() && recipientOpt.isPresent()) {
            chatMessage.setSender(senderOpt.get());
            chatMessage.setRecipient(recipientOpt.get());
            chatMessage.setTimestamp(new Date());
            chatMessage.setIsRead(false);

            // Lưu tin nhắn vào CSDL
            ChatMessage savedMessage = chatMessageRepo.save(chatMessage);

            // Gửi tới người nhận thông qua kênh cá nhân của họ
            messagingTemplate.convertAndSendToUser(
                recipientUsername,
                "/queue/messages",
                savedMessage
            );

            // Gửi ngược lại cho chính người gửi (để đồng bộ tin nhắn trên nhiều tab nếu mở)
            messagingTemplate.convertAndSendToUser(
                senderUsername,
                "/queue/messages",
                savedMessage
            );
        }
    }
}
