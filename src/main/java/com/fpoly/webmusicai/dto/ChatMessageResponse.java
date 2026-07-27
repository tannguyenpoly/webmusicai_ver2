package com.fpoly.webmusicai.dto;

import java.util.Date;

import com.fpoly.webmusicai.entity.ChatMessage;

public record ChatMessageResponse(
        Integer id,
        String sender,
        String recipient,
        String content,
        Date timestamp,
        Boolean isRead) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSender().getUsername(),
                message.getRecipient().getUsername(),
                message.getContent(),
                message.getTimestamp(),
                message.getIsRead());
    }
}
