package com.fpoly.webmusicai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.repository.FavoriteRepository;
import com.fpoly.webmusicai.repository.PlaylistSongRepository;
import com.fpoly.webmusicai.repository.SongCommentRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.SongTagRepository;

@Service
public class SongService {

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private FavoriteRepository favoriteRepo;

    @Autowired
    private SongCommentRepository commentRepo;

    @Autowired
    private PlaylistSongRepository playlistSongRepo;

    @Autowired
    private SongTagRepository songTagRepo;

    /**
     * Xóa một bài hát và tất cả dữ liệu liên quan một cách an toàn trong một giao dịch.
     * Các dữ liệu liên quan bao gồm:
     * - Lượt yêu thích (Favorites)
     * - Bình luận (Song_Comments)
     * - Liên kết trong các playlist (Playlist_Songs)
     * - Các tag (Song_Tags)
     * - Cập nhật các bản remix (nếu có) để không còn trỏ tới bài hát này.
     *
     * @param songId ID của bài hát cần xóa.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSongAndAssociatedData(Integer songId) {
        if (!songRepo.existsById(songId)) {
            throw new RuntimeException("Bài hát với ID " + songId + " không tồn tại.");
        }

        favoriteRepo.deleteBySongId(songId);
        commentRepo.deleteBySongId(songId);
        playlistSongRepo.deleteBySongId(songId);
        songTagRepo.deleteBySongId(songId);
        songRepo.nullifyParentIdForRemixes(songId);
        songRepo.deleteById(songId);
    }
}