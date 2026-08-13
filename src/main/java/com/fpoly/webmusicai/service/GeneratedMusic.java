package com.fpoly.webmusicai.service;

public record GeneratedMusic(
        String title,
        byte[] audioBytes,
        String contentType,
        String lyrics,
        String externalTaskId,
        String providerStatus) {

    /**
     * Giữ tương thích với các luồng tạo nhạc/test cũ chưa có mã tác vụ của
     * nhà cung cấp. Các provider mới nên dùng constructor đầy đủ.
     */
    public GeneratedMusic(String title, byte[] audioBytes, String contentType, String lyrics) {
        this(title, audioBytes, contentType, lyrics, null, "COMPLETED");
    }
}
