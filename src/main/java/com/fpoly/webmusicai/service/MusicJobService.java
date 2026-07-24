package com.fpoly.webmusicai.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MusicJobService {

    private final MusicGeneratorService musicGeneratorService;
    private final SongGenerationService songGenerationService;
    private final SongNotificationService songNotificationService;
    private final ThreadPoolTaskExecutor musicTaskExecutor;
    private final ConcurrentMap<Integer, Future<?>> runningJobs = new ConcurrentHashMap<>();

    public MusicJobService(
            MusicGeneratorService musicGeneratorService,
            SongGenerationService songGenerationService,
            SongNotificationService songNotificationService,
            @Qualifier("musicTaskExecutor") ThreadPoolTaskExecutor musicTaskExecutor) {
        this.musicGeneratorService = musicGeneratorService;
        this.songGenerationService = songGenerationService;
        this.songNotificationService = songNotificationService;
        this.musicTaskExecutor = musicTaskExecutor;
    }

    public void submit(Integer songId, String prompt, String requestedTitle, boolean instrumental) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            runGeneration(songId, prompt, requestedTitle, instrumental);
            return null;
        });
        if (runningJobs.putIfAbsent(songId, task) != null) {
            throw new IllegalStateException("Tác vụ tạo nhạc đã tồn tại");
        }
        try {
            musicTaskExecutor.execute(task);
        } catch (RuntimeException e) {
            runningJobs.remove(songId, task);
            throw e;
        }
    }

    public boolean cancel(Integer songId) {
        Future<?> future = runningJobs.remove(songId);
        return future != null && future.cancel(true);
    }

    private void runGeneration(Integer songId, String prompt, String requestedTitle, boolean instrumental) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            GeneratedMusic generatedMusic = musicGeneratorService.generateMusic(prompt, instrumental);
            boolean completed = songGenerationService.complete(songId, generatedMusic, requestedTitle);
            if (completed) {
                try {
                    songNotificationService.notifyFollowersForPublicSong(songId);
                } catch (RuntimeException notificationError) {
                    log.warn("Không thể tạo thông báo follower cho bài #{}: {}",
                            songId, notificationError.getMessage());
                }
                log.info("Đã tạo và lưu file âm thanh cho bài hát #{}", songId);
            }
        } catch (Exception e) {
            log.error("Tạo nhạc thất bại cho bài #{}: {}", songId, e.getMessage());
            songGenerationService.failAndRefund(songId, e.getMessage());
        } finally {
            runningJobs.remove(songId);
        }
    }
}
