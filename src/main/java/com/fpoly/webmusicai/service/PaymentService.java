package com.fpoly.webmusicai.service;

import java.util.Calendar;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.PaymentLog;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.OrderRepository;
import com.fpoly.webmusicai.repository.PaymentLogRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentLogRepository paymentLogRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MailService mailService;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentLogRepository paymentLogRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            MailService mailService) {
        this.orderRepository = orderRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.mailService = mailService;
    }

    @Transactional
    public PaymentCompletionResult complete(
            String orderCode,
            String gateway,
            String transactionId,
            int receivedAmount,
            String rawContent) {
        if (transactionId == null || transactionId.isBlank()
                || paymentLogRepository.existsByTransactionId(transactionId)) {
            return new PaymentCompletionResult("DUPLICATE", "Giao dịch đã được xử lý trước đó");
        }

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        String previousStatus = order.getStatus() == null ? "PENDING" : order.getStatus().toUpperCase();
        if ("SUCCESS".equals(previousStatus)) {
            return new PaymentCompletionResult("DUPLICATE", "Đơn hàng đã thanh toán thành công");
        }
        if (!java.util.Set.of("PENDING", "CANCELLED", "EXPIRED").contains(previousStatus)) {
            return saveForReview(order, gateway, transactionId, receivedAmount, rawContent,
                    "Đơn đang ở trạng thái " + previousStatus + ", cần Admin đối soát");
        }
        if (receivedAmount != order.getTotalPrice()) {
            return saveForReview(order, gateway, transactionId, receivedAmount, rawContent,
                    "Số tiền không khớp. Cần " + order.getTotalPrice() + ", nhận " + receivedAmount);
        }

        PaymentLog log = new PaymentLog();
        log.setOrderCode(orderCode);
        log.setGatewayName(gateway);
        log.setTransactionId(transactionId);
        log.setAmount(receivedAmount);
        log.setContent(rawContent);
        paymentLogRepository.save(log);

        order.setStatus("SUCCESS");
        orderRepository.save(order);
        creditOrderBenefits(order, "Thanh toán thành công qua " + gateway + " - Mã: " + orderCode);
        String message = ("CANCELLED".equals(previousStatus) || "EXPIRED".equals(previousStatus))
                ? "Đã nhận thanh toán muộn hợp lệ và cộng token"
                : "Thanh toán thành công";
        return new PaymentCompletionResult("SUCCESS", message);
    }

    @Transactional
    public PaymentCompletionResult approveReview(String orderCode) {
        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (!"REVIEW".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Chỉ đơn CẦN ĐỐI SOÁT mới có thể xác nhận thủ công");
        }

        order.setStatus("SUCCESS");
        orderRepository.save(order);
        creditOrderBenefits(order, "Admin xác nhận đối soát - Mã: " + orderCode);
        return new PaymentCompletionResult("SUCCESS", "Admin đã xác nhận và cộng token cho người dùng");
    }

    private PaymentCompletionResult saveForReview(
            Order order,
            String gateway,
            String transactionId,
            int receivedAmount,
            String rawContent,
            String reason) {
        PaymentLog log = new PaymentLog();
        log.setOrderCode(order.getOrderCode());
        log.setGatewayName(gateway);
        log.setTransactionId(transactionId);
        log.setAmount(receivedAmount);
        log.setContent("[CẦN ĐỐI SOÁT] " + reason + "\n" + (rawContent == null ? "" : rawContent));
        paymentLogRepository.save(log);

        order.setStatus("REVIEW");
        orderRepository.save(order);
        return new PaymentCompletionResult("REVIEW", reason);
    }

    private void creditOrderBenefits(Order order, String transactionDescription) {
        User user = userRepository.findByUsernameForUpdate(order.getUser().getUsername())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người mua"));
        user.setTokenBalance((user.getTokenBalance() == null ? 0 : user.getTokenBalance())
                + order.getPkg().getTokens());
        String purchasedTier = order.getPkg().getTierCode();
        if (purchasedTier != null && !purchasedTier.isBlank()
                && !"FREE".equalsIgnoreCase(purchasedTier)) {
            user.setAccountTier(purchasedTier.toUpperCase());
            Calendar calendar = Calendar.getInstance();
            if (user.getProExpiredAt() != null && user.getProExpiredAt().after(new Date())) {
                calendar.setTime(user.getProExpiredAt());
            }
            int durationDays = order.getPkg().getDurationDays() == null
                    ? 30
                    : Math.max(1, order.getPkg().getDurationDays());
            calendar.add(Calendar.DAY_OF_MONTH, durationDays);
            user.setProExpiredAt(calendar.getTime());
        }
        userRepository.save(user);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(order.getPkg().getTokens());
        transaction.setDescription(transactionDescription);
        transactionRepository.save(transaction);
        mailService.sendInvoiceEmail(user, order);
    }
}
