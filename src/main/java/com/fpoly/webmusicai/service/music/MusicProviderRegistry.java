package com.fpoly.webmusicai.service.music;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class MusicProviderRegistry {
    private final Map<String, MusicGenerationProvider> providers = new LinkedHashMap<>();

    public MusicProviderRegistry(Collection<MusicGenerationProvider> providerList) {
        providerList.forEach(provider -> providers.put(provider.code().toLowerCase(Locale.ROOT), provider));
    }

    public MusicGenerationProvider getRequired(String code) {
        String normalized = code == null || code.isBlank() ? "audiocraft" : code.trim().toLowerCase(Locale.ROOT);
        MusicGenerationProvider provider = providers.get(normalized);
        if (provider == null) throw new IllegalArgumentException("Mô hình AI không hợp lệ: " + normalized);
        return provider;
    }

    public Map<String, Object> getCapabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        providers.values().forEach(provider -> result.put(provider.code(), Map.of(
                "code", provider.code(),
                "name", provider.displayName(),
                "available", provider.isAvailable(),
                "supportsInstrumental", true,
                "supportsLyrics", !"audiocraft".equals(provider.code()),
                "supportsVocalLanguage", "ace-step".equals(provider.code()),
                "supportsShortPreview", !"suno".equals(provider.code()))));
        return result;
    }
}
