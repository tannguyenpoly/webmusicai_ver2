package com.fpoly.webmusicai.service.music;

import com.fpoly.webmusicai.service.GeneratedMusic;

/** Một adapter độc lập cho mỗi nhà cung cấp AI. */
public interface MusicGenerationProvider {

    String code();

    String displayName();

    /** Kiểm tra URL/key trước khi trừ token của người dùng. */
    boolean isAvailable();

    default boolean supportsInstrumental() { return true; }

    default boolean supportsLyrics() { return true; }

    default boolean supportsVocalLanguage() { return false; }

    default boolean supportsVocalGender() { return false; }

    GeneratedMusic generate(GenerationSpec spec);
}
