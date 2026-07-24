package com.fpoly.webmusicai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;

class SongGenerationServiceTests {

    @Test
    void lastTokenCanOnlyBeConsumedOnce() {
        UserRepository users = mock(UserRepository.class);
        SongRepository songs = mock(SongRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        AudioStorageService storage = mock(AudioStorageService.class);
        SongGenerationService service = new SongGenerationService(users, songs, transactions, storage);

        User user = new User();
        user.setUsername("demo");
        user.setTokenBalance(1);
        user.setAccountTier("BASIC");
        when(users.findByUsernameForUpdate("demo")).thenReturn(Optional.of(user));
        when(songs.save(any(Song.class))).thenAnswer(invocation -> {
            Song song = invocation.getArgument(0);
            song.setId(10);
            return song;
        });

        service.createPendingSong("demo", "lofi", "Demo");

        assertEquals(0, user.getTokenBalance());
        assertThrows(IllegalStateException.class,
                () -> service.createPendingSong("demo", "lofi lần hai", "Demo 2"));
        verify(transactions, times(1)).save(any(Transaction.class));
    }

    @Test
    void failedJobIsRefundedExactlyOnce() {
        UserRepository users = mock(UserRepository.class);
        SongRepository songs = mock(SongRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        AudioStorageService storage = mock(AudioStorageService.class);
        SongGenerationService service = new SongGenerationService(users, songs, transactions, storage);

        User user = new User();
        user.setUsername("demo");
        user.setTokenBalance(0);
        Song song = new Song();
        song.setId(11);
        song.setStatus("PENDING");
        song.setUser(user);
        when(songs.findByIdForUpdate(11)).thenReturn(Optional.of(song));
        when(users.findByUsernameForUpdate("demo")).thenReturn(Optional.of(user));

        service.failAndRefund(11, "AI timeout");
        service.failAndRefund(11, "callback lặp");

        assertEquals("FAILED", song.getStatus());
        assertEquals(1, user.getTokenBalance());
        verify(transactions, times(1)).save(any(Transaction.class));
    }

    @Test
    void cancelledJobIsRefundedExactlyOnceAndCannotCompleteLater() {
        UserRepository users = mock(UserRepository.class);
        SongRepository songs = mock(SongRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        AudioStorageService storage = mock(AudioStorageService.class);
        SongGenerationService service = new SongGenerationService(users, songs, transactions, storage);

        User user = new User();
        user.setUsername("demo");
        user.setTokenBalance(0);
        Song song = new Song();
        song.setId(12);
        song.setStatus("PENDING");
        song.setUser(user);
        when(songs.findByIdForUpdate(12)).thenReturn(Optional.of(song));
        when(users.findByUsernameForUpdate("demo")).thenReturn(Optional.of(user));

        SongCancellationResult result = service.cancelAndRefund(12, "demo", false);

        assertEquals("CANCELLED", result.status());
        assertEquals(1, result.remainingTokens());
        assertThrows(IllegalStateException.class,
                () -> service.cancelAndRefund(12, "demo", false));
        boolean completed = service.complete(
                12,
                new GeneratedMusic("Demo", new byte[] { 1, 2, 3 }, "audio/wav", null),
                "Demo");

        assertEquals(false, completed);
        assertEquals("CANCELLED", song.getStatus());
        assertEquals(1, user.getTokenBalance());
        verify(transactions, times(1)).save(any(Transaction.class));
        verify(storage, times(0)).store(any(byte[].class), any(String.class));
    }
}
