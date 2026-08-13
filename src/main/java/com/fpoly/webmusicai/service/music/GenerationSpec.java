package com.fpoly.webmusicai.service.music;

/**
 * Thông tin chuẩn mà Wizard gửi cho bất kỳ mô hình tạo nhạc nào.
 * Không chứa API key hay thông tin nhà cung cấp.
 */
public record GenerationSpec(
        String provider,
        String prompt,
        boolean instrumental,
        String lyrics,
        String vocalMode,
        String vocalLanguage,
        Integer durationSeconds) {

    public boolean hasLyrics() {
        return lyrics != null && !lyrics.isBlank();
    }
}
