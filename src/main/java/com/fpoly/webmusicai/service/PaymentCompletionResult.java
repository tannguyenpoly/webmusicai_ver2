package com.fpoly.webmusicai.service;

/**
 * Kết quả xử lý một biến động tiền gửi từ cổng thanh toán.
 * REVIEW nghĩa là đã lưu giao dịch để Admin đối soát, chưa cộng Credit.
 */
public record PaymentCompletionResult(String status, String message) {

    public boolean completed() {
        return "SUCCESS".equals(status);
    }
}
