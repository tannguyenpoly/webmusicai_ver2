package com.fpoly.webmusicai.service;

import java.util.Date;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.exception.TierRestrictedException;

/** Fixed feature matrix for the three purchasable plans. */
@Service
public class TierAccessService {

    public String effectiveTier(User user) {
        String tier = user == null || user.getAccountTier() == null ? "FREE"
                : user.getAccountTier().trim().toUpperCase(Locale.ROOT);
        if ("BASIC".equals(tier)) tier = "FREE";
        if (user != null && user.getProExpiredAt() != null && user.getProExpiredAt().before(new Date())) {
            return "FREE";
        }
        return Set.of("FREE", "CREATOR", "PRO", "STUDIO").contains(tier) ? tier : "FREE";
    }

    public boolean canUseReferenceAnalysis(User user) {
        String tier = effectiveTier(user);
        return "PRO".equals(tier) || "STUDIO".equals(tier);
    }

    public void requireReferenceAnalysis(User user) {
        if (!canUseReferenceAnalysis(user)) {
            throw new TierRestrictedException("Phân tích nhạc tham khảo dành cho gói PRO hoặc STUDIO.");
        }
    }

    public void requireGenerationAccess(User user, String provider, Integer durationSeconds, String vocalMode) {
        String tier = effectiveTier(user);
        String normalizedProvider = provider == null ? "audiocraft" : provider.trim().toLowerCase(Locale.ROOT);
        int duration = durationSeconds == null ? 30 : durationSeconds;
        String mode = vocalMode == null ? "instrumental" : vocalMode.trim().toLowerCase(Locale.ROOT);

        Set<String> allowedProviders = switch (tier) {
            case "CREATOR" -> Set.of("audiocraft", "musicapi");
            case "PRO", "STUDIO" -> Set.of("audiocraft", "ace-step", "musicapi", "suno");
            default -> Set.of("audiocraft");
        };
        if (!allowedProviders.contains(normalizedProvider)) {
            throw new TierRestrictedException("Mô hình " + providerLabel(normalizedProvider)
                    + " chưa có trong gói " + tierLabel(tier) + ".");
        }

        int maxDuration = "STUDIO".equals(tier) ? 120 : "PRO".equals(tier) ? 60 : 30;
        if (duration > maxDuration) {
            throw new TierRestrictedException("Gói " + tierLabel(tier) + " hỗ trợ tối đa "
                    + durationLabel(maxDuration) + ".");
        }

        if ("own-lyrics".equals(mode) && !("PRO".equals(tier) || "STUDIO".equals(tier))) {
            throw new TierRestrictedException("Tự nhập lời nhạc dành cho gói PRO hoặc STUDIO.");
        }
        if (!"instrumental".equals(mode) && !"ai-lyrics".equals(mode) && !"own-lyrics".equals(mode)) {
            throw new IllegalArgumentException("Chế độ lời nhạc không hợp lệ.");
        }
        if ("FREE".equals(tier) && !"instrumental".equals(mode)) {
            throw new TierRestrictedException("Gói FREE chỉ hỗ trợ nhạc không lời.");
        }
    }

    private String tierLabel(String tier) {
        return switch (tier) {
            case "CREATOR" -> "CREATOR";
            case "PRO" -> "PRO";
            case "STUDIO" -> "STUDIO";
            default -> "FREE";
        };
    }

    private String providerLabel(String provider) {
        return switch (provider) {
            case "ace-step" -> "ACE-Step";
            case "musicapi" -> "MusicAPI.ai";
            case "suno" -> "Suno";
            default -> "AudioCraft";
        };
    }

    private String durationLabel(int seconds) {
        return seconds >= 120 ? "2 phút" : seconds + " giây";
    }
}
