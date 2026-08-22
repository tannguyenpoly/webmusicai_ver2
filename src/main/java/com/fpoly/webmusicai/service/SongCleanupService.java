package com.fpoly.webmusicai.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.repository.AlbumSongRepository;
import com.fpoly.webmusicai.repository.FavoriteRepository;
import com.fpoly.webmusicai.repository.PlaylistSongRepository;
import com.fpoly.webmusicai.repository.SongCommentRepository;
import com.fpoly.webmusicai.repository.SongListenHistoryRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.SongTagRepository;

@Service
public class SongCleanupService {

    private final SongRepository songRepo;
    private final FavoriteRepository favoriteRepo;
    private final SongCommentRepository commentRepo;
    private final PlaylistSongRepository playlistSongRepo;
    private final AlbumSongRepository albumSongRepo;
    private final SongTagRepository songTagRepo;
    private final SongListenHistoryRepository listenHistoryRepo;
    private final AudioStorageService audioStorageService;

    public SongCleanupService(SongRepository songRepo,
                              FavoriteRepository favoriteRepo,
                              SongCommentRepository commentRepo,
                              PlaylistSongRepository playlistSongRepo,
                              AlbumSongRepository albumSongRepo,
                              SongTagRepository songTagRepo,
                              SongListenHistoryRepository listenHistoryRepo,
                              AudioStorageService audioStorageService) {
        this.songRepo = songRepo;
        this.favoriteRepo = favoriteRepo;
        this.commentRepo = commentRepo;
        this.playlistSongRepo = playlistSongRepo;
        this.albumSongRepo = albumSongRepo;
        this.songTagRepo = songTagRepo;
        this.listenHistoryRepo = listenHistoryRepo;
        this.audioStorageService = audioStorageService;
    }

    @Transactional
    public void deleteSongWithRelations(Song song) {
        Integer songId = song.getId();

        favoriteRepo.deleteBySongId(songId);
        commentRepo.deleteBySongId(songId);
        playlistSongRepo.deleteBySongId(songId);
        albumSongRepo.deleteBySongId(songId);
        songTagRepo.deleteBySongId(songId);
        listenHistoryRepo.deleteBySongId(songId);
        songRepo.deleteSongGenresBySongId(songId);
        songRepo.detachRemixesFromParent(songId);
        audioStorageService.deleteByUrl(song.getAudioUrl());
        songRepo.delete(song);
    }
}
