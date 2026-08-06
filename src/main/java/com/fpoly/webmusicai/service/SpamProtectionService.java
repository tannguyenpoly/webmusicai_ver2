package com.fpoly.webmusicai.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Bộ chặn gửi thao tác lặp lại tối giản cho bản demo.
 * Dữ liệu chỉ nằm trong bộ nhớ nên sẽ tự làm mới khi ứng dụng khởi động lại.
 */
@Service
public class SpamProtectionService {

    private final Map<String, Long> lastActionAt = new HashMap<>();

    /**
     * @return số giây còn cần chờ; 0 nghĩa là thao tác được chấp nhận.
     */
    public synchronized long remainingSeconds(String username, String action, long cooldownMillis) {
        long now = System.currentTimeMillis();
        String key = username + ':' + action;
        Long lastTime = lastActionAt.get(key);
        if (lastTime == null || now - lastTime >= cooldownMillis) {
            lastActionAt.put(key, now);
            return 0;
        }
        return Math.max(1, (long) Math.ceil((cooldownMillis - (now - lastTime)) / 1000.0));
    }
}
