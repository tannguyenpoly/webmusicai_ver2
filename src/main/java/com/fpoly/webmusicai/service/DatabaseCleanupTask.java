package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Calendar;
import java.util.Date;

@Component
@Slf4j
public class DatabaseCleanupTask {

	private final SongGenerationService songGenerationService;
	private final OrderLifecycleService orderLifecycleService;

	public DatabaseCleanupTask(
			SongGenerationService songGenerationService,
			OrderLifecycleService orderLifecycleService) {
		this.songGenerationService = songGenerationService;
		this.orderLifecycleService = orderLifecycleService;
	}

	@Scheduled(cron = "${music.cleanup.recover-cron:0 */10 * * * *}")
	public void recoverStuckJobs() {
		Calendar cutoff = Calendar.getInstance();
		cutoff.add(Calendar.MINUTE, -30);
		int refunded = songGenerationService.refundStuckPendingSongs(cutoff.getTime());
		if (refunded > 0) {
			log.warn("Đã đánh dấu FAILED và hoàn token cho {} tác vụ PENDING quá 30 phút", refunded);
		}
		int expiredOrders = orderLifecycleService.expirePendingOrders();
		if (expiredOrders > 0) {
			log.info("Đã đóng {} đơn thanh toán PENDING quá 15 phút", expiredOrders);
		}
	}

	@Scheduled(cron = "${music.cleanup.delete-cron:0 0 2 * * *}")
	public void deleteOldFailures() {
		Calendar cutoff = Calendar.getInstance();
		cutoff.add(Calendar.DAY_OF_MONTH, -7);
		int deleted = songGenerationService.deleteOldFailedSongs(new Date(cutoff.getTimeInMillis()));
		log.info("Cleanup lúc 02:00: đã xóa {} tác vụ FAILED quá 7 ngày", deleted);
	}
}
