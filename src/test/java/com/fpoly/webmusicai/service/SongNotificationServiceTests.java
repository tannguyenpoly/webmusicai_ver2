package com.fpoly.webmusicai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FollowRepository;
import com.fpoly.webmusicai.repository.NotificationRepository;
import com.fpoly.webmusicai.repository.SongRepository;

class SongNotificationServiceTests {

    @Test
    void publicCompletedSongNotifiesFollowersOnlyOnce() {
        SongRepository songs = mock(SongRepository.class);
        FollowRepository follows = mock(FollowRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        SongNotificationService service = new SongNotificationService(songs, follows, notifications);

        User author = new User();
        author.setUsername("artist");
        User follower = new User();
        follower.setUsername("listener");
        Song song = new Song();
        song.setId(21);
        song.setTitle("Bài mới");
        song.setStatus("COMPLETED");
        song.setIsPublic(true);
        song.setUser(author);

        when(songs.findById(21)).thenReturn(Optional.of(song));
        when(follows.findFollowersList("artist")).thenReturn(List.of(follower));
        when(notifications.existsByUserUsernameAndTypeAndRefId(
                "listener", SongNotificationService.NEW_SONG, 21))
                .thenReturn(false, true);

        assertEquals(1, service.notifyFollowersForPublicSong(21));
        assertEquals(0, service.notifyFollowersForPublicSong(21));
        verify(notifications, times(1)).saveAll(anyList());
    }

    @Test
    void privateSongDoesNotNotifyFollowers() {
        SongRepository songs = mock(SongRepository.class);
        FollowRepository follows = mock(FollowRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        SongNotificationService service = new SongNotificationService(songs, follows, notifications);

        Song song = new Song();
        song.setId(22);
        song.setStatus("COMPLETED");
        song.setIsPublic(false);
        when(songs.findById(22)).thenReturn(Optional.of(song));

        assertEquals(0, service.notifyFollowersForPublicSong(22));
        verify(notifications, times(0)).saveAll(anyList());
    }
}
