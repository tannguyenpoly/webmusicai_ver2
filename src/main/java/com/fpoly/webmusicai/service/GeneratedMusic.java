package com.fpoly.webmusicai.service;

public record GeneratedMusic(
        String title,
        byte[] audioBytes,
        String contentType,
        String lyrics) {
}
