package com.fpoly.webmusicai.controller;

import com.fpoly.webmusicai.entity.PaymentLog;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.repository.PaymentLogRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminTransactionController {

    @Autowired
    private TransactionRepository transactionRepo;

    @Autowired
    private PaymentLogRepository paymentLogRepo;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "ALL") String type, // ALL, DEPOSIT, USAGE
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Yêu cầu quyền Quản trị viên."));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String usernameFilter = (username != null && !username.isBlank()) ? username.trim() : null;
        
        Page<Transaction> transactionPage = transactionRepo.findFilteredTransactions(usernameFilter, type, pageable);
        
        Page<Map<String, Object>> resultPage = transactionPage.map(transaction -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", transaction.getId());
            if (transaction.getUser() != null) {
                map.put("username", transaction.getUser().getUsername());
                map.put("fullname", transaction.getUser().getFullname());
            }
            map.put("amount", transaction.getAmount());
            map.put("description", transaction.getDescription());
            map.put("createdAt", transaction.getCreatedAt());
            return map;
        });
        return ResponseEntity.ok(resultPage);
    }

    @GetMapping("/payment-logs")
    public ResponseEntity<?> getPaymentLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Yêu cầu quyền Quản trị viên."));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String keywordFilter = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        
        Page<PaymentLog> logPage = paymentLogRepo.findByKeyword(keywordFilter, pageable);

        return ResponseEntity.ok(logPage);
    }
}
