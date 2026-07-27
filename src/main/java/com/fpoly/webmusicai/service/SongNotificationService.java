package com.fpoly.webmusicai.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Notification;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FollowRepository;
import com.fpoly.webmusicai.repository.NotificationRepository;
import com.fpoly.webmusicai.repository.SongRepository;

@Service
public class SongNotificationService {
    public static final String NEW_SONG = "FOLLOWING_NEW_SONG";

    private final SongRepository songRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;

    public SongNotificationService(
            SongRepository songRepository,
            FollowRepository followRepository,
            NotificationRepository notificationRepository) {
        this.songRepository = songRepository;
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public int notifyFollowersForPublicSong(Integer songId) {
        Song song = songRepository.findById(songId).orElse(null);
        if (song == null || !"COMPLETED".equals(song.getStatus())
                || !Boolean.TRUE.equals(song.getIsPublic()) || song.getUser() == null) {
            return 0;
        }

        List<User> followers = followRepository.findFollowersList(song.getUser().getUsername());
        List<Notification> newNotifications = new ArrayList<>();
        for (User follower : followers) {
            if (notificationRepository.existsByUserUsernameAndTypeAndRefId(
                    follower.getUsername(), NEW_SONG, songId)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUser(follower);
            notification.setType(NEW_SONG);
            notification.setRefId(songId);
            notification.setContent(shorten(
                    song.getUser().getUsername() + " vừa phát hành bài \"" + song.getTitle() + "\"",
                    255));
            newNotifications.add(notification);
        }
        if (newNotifications.isEmpty()) {
            return 0;
        }
        notificationRepository.saveAll(newNotifications);
        return newNotifications.size();
    }

    private String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
