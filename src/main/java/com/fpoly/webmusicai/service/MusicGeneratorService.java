package com.fpoly.webmusicai.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fpoly.webmusicai.service.music.GenerationSpec;
import com.fpoly.webmusicai.service.music.MusicGenerationProvider;
import com.fpoly.webmusicai.service.music.MusicProviderRegistry;

/** Cổng chung để job tạo nhạc gọi đúng provider người dùng đã chọn. */
@Service
public class MusicGeneratorService {
    private final MusicProviderRegistry providerRegistry;

    public MusicGeneratorService(MusicProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public boolean isAvailable(String providerCode) {
        return providerRegistry.getRequired(providerCode).isAvailable();
    }

    /**
     * Frontend chỉ là lớp hướng dẫn. API vẫn phải từ chối lựa chọn mà mô hình
     * không hỗ trợ để tránh request thủ công gửi dữ liệu không tương thích.
     */
    public void validateCapabilities(GenerationSpec spec) {
        MusicGenerationProvider provider = providerRegistry.getRequired(spec.provider());
        if (!spec.instrumental() && !provider.supportsLyrics()) {
            throw new IllegalArgumentException(provider.displayName() + " chỉ hỗ trợ nhạc không lời.");
        }
        String gender = spec.vocalGender() == null ? "auto" : spec.vocalGender().trim().toLowerCase();
        if (!"auto".equals(gender) && !"male".equals(gender) && !"female".equals(gender)) {
            throw new IllegalArgumentException("Lựa chọn giọng hát không hợp lệ.");
        }
        if (!spec.instrumental() && !"auto".equals(gender) && !provider.supportsVocalGender()) {
            throw new IllegalArgumentException(provider.displayName() + " chưa hỗ trợ chọn giọng nam hoặc nữ.");
        }
    }

    public GeneratedMusic generateMusic(GenerationSpec spec) {
        MusicGenerationProvider provider = providerRegistry.getRequired(spec.provider());
        return provider.generate(spec);
    }

    public Map<String, Object> getCapabilities() {
        return providerRegistry.getCapabilities();
    }
}
