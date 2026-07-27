package com.fpoly.webmusicai.service;

public record SongCancellationResult(
        Integer songId,
        String status,
        int remainingTokens) {
}
