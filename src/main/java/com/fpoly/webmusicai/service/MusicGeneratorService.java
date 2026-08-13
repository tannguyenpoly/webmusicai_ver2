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

    public GeneratedMusic generateMusic(GenerationSpec spec) {
        MusicGenerationProvider provider = providerRegistry.getRequired(spec.provider());
        return provider.generate(spec);
    }

    public Map<String, Object> getCapabilities() {
        return providerRegistry.getCapabilities();
    }
}
