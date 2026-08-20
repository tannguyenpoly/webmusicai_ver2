package com.fpoly.webmusicai.service;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.exception.TierRestrictedException;

@Service
public class TierAccessService {

    public String effectiveTier(User user) {
        String tier = user.getAccountTier() == null ? "FREE" : user.getAccountTier().toUpperCase();
        if ("BASIC".equals(tier)) tier = "FREE";
        if (user.getProExpiredAt() != null && user.getProExpiredAt().before(new Date())) tier = "FREE";
        return tier;
    }

    public int tierLevel(String tier) {
        String t = tier == null ? "FREE" : tier.toUpperCase();
        if ("CREATOR".equals(t)) return 1;
        if ("PRO".equals(t) || "STUDIO".equals(t)) return 2;
        return 0;
    }

    public String tierLabel(String tier) {
        String t = tier == null ? "FREE" : tier.toUpperCase();
        return switch (t) {
            case "CREATOR" -> "Nhà sáng tạo";
            case "PRO" -> "Chuyên nghiệp";
            case "STUDIO" -> "Phòng thu";
            default -> "Miễn phí";
        };
    }

    public void requireGenreAccess(User user, Genre genre) {
        String required = genre.getMinTier() == null ? "FREE" : genre.getMinTier().toUpperCase();
        if (tierLevel(effectiveTier(user)) < tierLevel(required)) {
            throw new TierRestrictedException(
                    "Thể loại " + genre.getName() + " yêu cầu gói " + tierLabel(required) + " trở lên.");
        }
    }
}