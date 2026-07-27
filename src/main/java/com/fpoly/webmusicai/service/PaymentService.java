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
    private final OrderLifecycleService orderLifecycleService;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentLogRepository paymentLogRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            MailService mailService,
            OrderLifecycleService orderLifecycleService) {
        this.orderRepository = orderRepository;
        this.paymentLogRepository = paymentLogRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.mailService = mailService;
        this.orderLifecycleService = orderLifecycleService;
    }

    @Transactional
    public boolean complete(
            String orderCode,
            String gateway,
            String transactionId,
            int receivedAmount,
            String rawContent) {
        if (transactionId == null || transactionId.isBlank()
                || paymentLogRepository.existsByTransactionId(transactionId)) {
            return false;
        }

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (orderLifecycleService.expireIfNeeded(order)) {
            return false;
        }
        if ("SUCCESS".equals(order.getStatus())) {
            return false;
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng không còn chờ thanh toán");
        }
        if (receivedAmount != order.getTotalPrice()) {
            throw new IllegalArgumentException("Số tiền nhận không khớp giá trị đơn hàng");
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
        transaction.setDescription("Thanh toán thành công qua " + gateway + " - Mã: " + orderCode);
        transactionRepository.save(transaction);

        mailService.sendInvoiceEmail(user, order);
        return true;
    }
}
