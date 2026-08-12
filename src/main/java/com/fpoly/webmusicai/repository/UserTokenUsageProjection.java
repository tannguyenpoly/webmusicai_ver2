package com.fpoly.webmusicai.repository;

/** Tổng token đã bị trừ theo người dùng trong khoảng thời gian Admin đang xem. */
public interface UserTokenUsageProjection {
    String getUsername();
    Long getUsedTokens();
}
