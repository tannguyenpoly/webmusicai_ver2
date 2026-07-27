package com.fpoly.webmusicai.service;

public record SongGenerationTicket(
        Integer songId,
        Integer remainingTokens,
        Integer parentId) {
}
