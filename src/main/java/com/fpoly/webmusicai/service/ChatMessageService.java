package com.fpoly.webmusicai.service;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.dto.ChatMessageRequest;
import com.fpoly.webmusicai.dto.ChatMessageResponse;
import com.fpoly.webmusicai.entity.ChatMessage;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.ChatMessageRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public ChatMessageService(
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChatMessageResponse send(String senderUsername, ChatMessageRequest request) {
        User sender = userRepository.findById(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("Người gửi không tồn tại"));
        User recipient = userRepository.findById(request.recipientUsername())
                .orElseThrow(() -> new IllegalArgumentException("Người nhận không tồn tại"));

        ChatMessage message = new ChatMessage();
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(request.content().trim());
        message.setTimestamp(new Date());
        message.setIsRead(false);
        return ChatMessageResponse.from(chatMessageRepository.save(message));
    }
}
