package com.fpoly.webmusicai.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class SongGenerationService {

    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final TransactionRepository transactionRepository;
    private final AudioStorageService audioStorageService;

    public SongGenerationService(
            UserRepository userRepository,
            SongRepository songRepository,
            TransactionRepository transactionRepository,
            AudioStorageService audioStorageService) {
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.transactionRepository = transactionRepository;
        this.audioStorageService = audioStorageService;
    }

    @Transactional
    public SongGenerationTicket createPendingSong(String username, String prompt, String title) {
        User user = lockUserWithToken(username);
        deductToken(user, "Tạo nhạc: " + prompt);

        Song song = new Song();
        song.setTitle(title != null && !title.isBlank() ? title.trim() : "Đang tạo...");
        song.setPrompt(prompt.trim());
        song.setStatus("PENDING");
        song.setIsPublic(isFreeTier(user));
        song.setUser(user);
        songRepository.save(song);

        return new SongGenerationTicket(song.getId(), user.getTokenBalance(), null);
    }

    @Transactional
    public void assignGenre(Integer songId, Genre genre) {
        if (genre == null) {
            return;
        }
        Song song = songRepository.findByIdForUpdate(songId)
                .orElseThrow(() -> new IllegalArgumentException("Bài hát không tồn tại"));
        if (song.getGenres() == null) {
            song.setGenres(new ArrayList<>());
        } else {
            song.getGenres().clear();
        }
        song.getGenres().add(genre);
        songRepository.save(song);
    }

    @Transactional
    public SongGenerationTicket createPendingRemix(
            String username, Song original, String prompt, String title) {
        User user = lockUserWithToken(username);
        deductToken(user, "Remix nhạc từ bài: " + original.getTitle());

        Song remix = new Song();
        remix.setTitle(title != null && !title.isBlank()
                ? title.trim()
                : original.getTitle() + " (Remix)");
        remix.setPrompt(prompt.trim());
        remix.setStatus("PENDING");
        remix.setIsPublic(isFreeTier(user));
        remix.setIsRemix(true);
        remix.setParentId(original.getId());
        remix.setUser(user);
        songRepository.save(remix);

        return new SongGenerationTicket(remix.getId(), user.getTokenBalance(), original.getId());
    }

    @Transactional
    public boolean complete(Integer songId, GeneratedMusic generatedMusic, String requestedTitle) {
        Song song = songRepository.findByIdForUpdate(songId)
                .orElseThrow(() -> new IllegalArgumentException("Bài hát không tồn tại"));
        if (!"PENDING".equals(song.getStatus())) {
            return false;
        }

        String audioUrl = audioStorageService.store(
                generatedMusic.audioBytes(), generatedMusic.contentType());
        song.setAudioUrl(audioUrl);
        song.setLyrics(generatedMusic.lyrics());
        song.setStatus("COMPLETED");
        if (requestedTitle == null || requestedTitle.isBlank()) {
            song.setTitle(generatedMusic.title() != null && !generatedMusic.title().isBlank()
                    ? generatedMusic.title()
                    : "Bài hát không tên");
        }
        songRepository.save(song);
        return true;
    }

    @Transactional
    public void failAndRefund(Integer songId, String reason) {
        Song song = songRepository.findByIdForUpdate(songId).orElse(null);
        if (song == null || !"PENDING".equals(song.getStatus())) {
            return;
        }

        song.setStatus("FAILED");
        songRepository.save(song);

        User user = userRepository.findByUsernameForUpdate(song.getUser().getUsername())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy chủ bài hát"));
        user.setTokenBalance(user.getTokenBalance() + 1);
        userRepository.save(user);

        Transaction refund = new Transaction();
        refund.setUser(user);
        refund.setAmount(1);
        refund.setDescription("Hoàn token do tạo nhạc thất bại"
                + (reason == null || reason.isBlank() ? "" : ": " + shorten(reason, 120)));
        transactionRepository.save(refund);
    }

    @Transactional
    public SongCancellationResult cancelAndRefund(Integer songId, String username, boolean isAdmin) {
        Song song = songRepository.findByIdForUpdate(songId)
                .orElseThrow(() -> new IllegalArgumentException("Bài hát không tồn tại"));
        boolean isOwner = song.getUser() != null
                && song.getUser().getUsername().equals(username);
        if (!isOwner && !isAdmin) {
            throw new SecurityException("Bạn không có quyền dừng tác vụ này");
        }
        if (!"PENDING".equals(song.getStatus())) {
            throw new IllegalStateException("Chỉ có thể dừng bài nhạc đang được xử lý");
        }

        song.setStatus("CANCELLED");
        songRepository.save(song);

        User user = userRepository.findByUsernameForUpdate(song.getUser().getUsername())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy chủ bài hát"));
        user.setTokenBalance(user.getTokenBalance() + 1);
        userRepository.save(user);

        Transaction refund = new Transaction();
        refund.setUser(user);
        refund.setAmount(1);
        refund.setDescription("Hoàn token do người dùng dừng tạo nhạc");
        transactionRepository.save(refund);

        return new SongCancellationResult(songId, song.getStatus(), user.getTokenBalance());
    }

    @Transactional
    public int refundStuckPendingSongs(Date cutoff) {
        List<Song> stuckSongs = songRepository.findByStatusAndCreatedAtBefore("PENDING", cutoff);
        int refunded = 0;
        for (Song song : stuckSongs) {
            Song lockedSong = songRepository.findByIdForUpdate(song.getId()).orElse(null);
            if (lockedSong == null || !"PENDING".equals(lockedSong.getStatus())) {
                continue;
            }

            lockedSong.setStatus("FAILED");
            songRepository.save(lockedSong);

            User user = userRepository.findByUsernameForUpdate(lockedSong.getUser().getUsername())
                    .orElse(null);
            if (user != null) {
                user.setTokenBalance(user.getTokenBalance() + 1);
                userRepository.save(user);

                Transaction refund = new Transaction();
                refund.setUser(user);
                refund.setAmount(1);
                refund.setDescription("Hoàn token do tác vụ tạo nhạc quá thời gian");
                transactionRepository.save(refund);
                refunded++;
            }
        }
        return refunded;
    }

    @Transactional
    public int deleteOldFailedSongs(Date cutoff) {
        List<Song> failedSongs = songRepository.findByStatusAndCreatedAtBefore("FAILED", cutoff);
        int deleted = 0;
        for (Song song : failedSongs) {
            audioStorageService.deleteByUrl(song.getAudioUrl());
            songRepository.delete(song);
            deleted++;
        }
        return deleted;
    }

    private User lockUserWithToken(String username) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));
        if (user.getTokenBalance() == null || user.getTokenBalance() < 1) {
            throw new IllegalStateException("Bạn không đủ Token!");
        }
        return user;
    }

    private void deductToken(User user, String description) {
        user.setTokenBalance(user.getTokenBalance() - 1);
        userRepository.save(user);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(-1);
        transaction.setDescription(shorten(description, 250));
        transactionRepository.save(transaction);
    }

    private String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isFreeTier(User user) {
        String tier = user.getAccountTier();
        return tier == null || tier.isBlank()
                || "FREE".equalsIgnoreCase(tier)
                || "BASIC".equalsIgnoreCase(tier);
    }
}
