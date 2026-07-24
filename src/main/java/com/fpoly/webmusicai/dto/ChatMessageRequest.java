package com.fpoly.webmusicai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank(message = "Người nhận không được để trống")
        String recipientUsername,

        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(max = 500, message = "Tin nhắn không được vượt quá 500 ký tự")
        String content) {
}
