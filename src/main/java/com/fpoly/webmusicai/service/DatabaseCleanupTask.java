package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fpoly.webmusicai.repository.SongRepository;
import java.util.Calendar;
import java.sql.Date;  // ← đổi sang java.sql.Date

@Component
@Slf4j
public class DatabaseCleanupTask {

    @Autowired
    private SongRepository songRepository;

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupDatabase() {
        log.info("=== Bắt đầu dọn rác DB lúc 12h đêm ===");

        // 1. Xóa bài FAILED quá 7 ngày
        Calendar cal7 = Calendar.getInstance();
        cal7.add(Calendar.DAY_OF_MONTH, -7);
        songRepository.deleteOldFailedSongs(new Date(cal7.getTimeInMillis()));  // ← convert
        log.info("Đã xóa bài FAILED quá 7 ngày");

        // 2. Xóa bài PENDING quá 1 giờ
        Calendar cal1h = Calendar.getInstance();
        cal1h.add(Calendar.HOUR_OF_DAY, -1);
        songRepository.deleteStuckPendingSongs(new Date(cal1h.getTimeInMillis()));  // ← convert
        log.info("Đã xóa bài PENDING bị treo quá 1 giờ");

        log.info("=== Dọn rác DB hoàn thành ===");
    }
}