package com.fpoly.webmusicai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.entity.PaymentLog;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.OrderRepository;
import com.fpoly.webmusicai.repository.PaymentLogRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;

class PaymentServiceTests {

    @Test
    void validPaymentAfterCancellationStillCreditsTheUser() {
        OrderRepository orders = mock(OrderRepository.class);
        PaymentLogRepository paymentLogs = mock(PaymentLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        MailService mail = mock(MailService.class);
        PaymentService service = new PaymentService(orders, paymentLogs, users, transactions, mail);

        User user = new User();
        user.setUsername("demo");
        user.setTokenBalance(5);
        Package pkg = new Package();
        pkg.setPrice(3000);
        pkg.setTokens(20);
        pkg.setTierCode("CREATOR");
        pkg.setDurationDays(30);
        Order order = new Order();
        order.setOrderCode("SP1234567890");
        order.setStatus("CANCELLED");
        order.setTotalPrice(3000);
        order.setPkg(pkg);
        order.setUser(user);

        when(paymentLogs.existsByTransactionId("bank-001")).thenReturn(false);
        when(orders.findByOrderCodeForUpdate("SP1234567890")).thenReturn(Optional.of(order));
        when(users.findByUsernameForUpdate("demo")).thenReturn(Optional.of(user));

        PaymentCompletionResult result = service.complete("SP1234567890", "SEPAY", "bank-001", 3000, "payload");

        assertTrue(result.completed());
        assertEquals("SUCCESS", order.getStatus());
        assertEquals(25, user.getTokenBalance());
        verify(paymentLogs).save(any(PaymentLog.class));
        verify(transactions).save(any(Transaction.class));
        verify(mail).sendInvoiceEmail(user, order);
    }

    @Test
    void wrongAmountIsSavedForReviewWithoutAddingTokens() {
        OrderRepository orders = mock(OrderRepository.class);
        PaymentLogRepository paymentLogs = mock(PaymentLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        MailService mail = mock(MailService.class);
        PaymentService service = new PaymentService(orders, paymentLogs, users, transactions, mail);

        Order order = new Order();
        order.setOrderCode("SP1234567891");
        order.setStatus("PENDING");
        order.setTotalPrice(5000);

        when(paymentLogs.existsByTransactionId("bank-002")).thenReturn(false);
        when(orders.findByOrderCodeForUpdate("SP1234567891")).thenReturn(Optional.of(order));

        PaymentCompletionResult result = service.complete("SP1234567891", "SEPAY", "bank-002", 3000, "payload");

        assertEquals("REVIEW", result.status());
        assertEquals("REVIEW", order.getStatus());
        verify(paymentLogs).save(any(PaymentLog.class));
        verify(users, never()).findByUsernameForUpdate(any());
        verify(transactions, never()).save(any(Transaction.class));
        verify(mail, never()).sendInvoiceEmail(any(), any());
    }

    @Test
    void adminCanApproveAReviewedPaymentAfterManualVerification() {
        OrderRepository orders = mock(OrderRepository.class);
        PaymentLogRepository paymentLogs = mock(PaymentLogRepository.class);
        UserRepository users = mock(UserRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        MailService mail = mock(MailService.class);
        PaymentService service = new PaymentService(orders, paymentLogs, users, transactions, mail);

        User user = new User();
        user.setUsername("demo");
        user.setTokenBalance(10);
        Package pkg = new Package();
        pkg.setTokens(30);
        pkg.setTierCode("FREE");
        Order order = new Order();
        order.setOrderCode("SP1234567892");
        order.setStatus("REVIEW");
        order.setUser(user);
        order.setPkg(pkg);

        when(orders.findByOrderCodeForUpdate("SP1234567892")).thenReturn(Optional.of(order));
        when(users.findByUsernameForUpdate("demo")).thenReturn(Optional.of(user));

        PaymentCompletionResult result = service.approveReview("SP1234567892");

        assertTrue(result.completed());
        assertEquals("SUCCESS", order.getStatus());
        assertEquals(40, user.getTokenBalance());
        verify(transactions).save(any(Transaction.class));
        verify(mail).sendInvoiceEmail(user, order);
    }
}
