package com.fpoly.webmusicai.repository;

import java.util.Date;

/** Dữ liệu tổng hợp phục vụ tab khách hàng tiềm năng của Admin. */
public interface UserSpendingProjection {
    String getUsername();
    String getFullname();
    String getEmail();
    String getAccountTier();
    Integer getTokenBalance();
    Long getSuccessfulOrderCount();
    Long getTotalSpent();
    Long getPurchasedTokens();
    Date getLastPaidAt();
}
