package com.fpoly.webmusicai.service.music;

import com.fpoly.webmusicai.service.GeneratedMusic;

/** Một adapter độc lập cho mỗi nhà cung cấp AI. */
public interface MusicGenerationProvider {

    String code();

    String displayName();

    /** Kiểm tra URL/key trước khi trừ token của người dùng. */
    boolean isAvailable();

    GeneratedMusic generate(GenerationSpec spec);
}
