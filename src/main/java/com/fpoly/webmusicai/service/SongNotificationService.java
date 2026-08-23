package com.fpoly.webmusicai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Notification;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.SongComment;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FollowRepository;
import com.fpoly.webmusicai.repository.NotificationRepository;
import com.fpoly.webmusicai.repository.SongRepository;

@Service
public class SongNotificationService {
    public static final String NEW_SONG = "FOLLOWING_NEW_SONG";
    public static final String NEW_COMMENT = "NEW_COMMENT";
    public static final String NEW_LIKE = "NEW_LIKE";

    private final SongRepository songRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SongNotificationService(
            SongRepository songRepository,
            FollowRepository followRepository,
            NotificationRepository notificationRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.songRepository = songRepository;
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
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

        for (Notification notification : newNotifications) {
            sendWebSocketNotification(notification);
        }

        return newNotifications.size();
    }

    @Transactional
    public void notifyNewComment(Song song, User commenter, SongComment comment) {
        if (song.getUser() == null || song.getUser().getUsername().equals(commenter.getUsername())) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(song.getUser());
        notification.setType(NEW_COMMENT);
        notification.setRefId(song.getId());
        notification.setContent(shorten(
                commenter.getUsername() + " đã bình luận về bài nhạc \"" + song.getTitle() + "\"",
                255));
        notificationRepository.save(notification);
        sendWebSocketNotification(notification);
    }

    @Transactional
    public void notifyNewLike(Song song, User liker) {
        if (song.getUser() == null || song.getUser().getUsername().equals(liker.getUsername())) {
            return;
        }
        // Tránh thông báo lặp nếu người dùng like nhiều lần liên tiếp (un-like rồi like lại)
        if (notificationRepository.existsByUserUsernameAndTypeAndRefId(song.getUser().getUsername(), NEW_LIKE, song.getId())) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(song.getUser());
        notification.setType(NEW_LIKE);
        notification.setRefId(song.getId());
        notification.setContent(shorten(
                liker.getUsername() + " đã yêu thích bài nhạc \"" + song.getTitle() + "\"",
                255));
        notificationRepository.save(notification);
        sendWebSocketNotification(notification);
    }

    private void sendWebSocketNotification(Notification notification) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", notification.getId());
            payload.put("type", notification.getType());
            payload.put("content", notification.getContent());
            payload.put("read", false);
            payload.put("refId", notification.getRefId());
            payload.put("createdAt", notification.getCreatedAt());

            messagingTemplate.convertAndSendToUser(
                    notification.getUser().getUsername(),
                    "/queue/notifications",
                    payload);
        } catch (Exception e) {
            // Ignore error
        }
    }

    private String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
