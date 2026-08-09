package com.fpoly.webmusicai.controller;

import com.fpoly.webmusicai.entity.PaymentLog;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.repository.PaymentLogRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Yêu cầu quyền Quản trị viên."));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(23, 59, 59)));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(root.get("user").get("username"), "%" + username.trim() + "%"));
            }
            if (type != null && !"ALL".equalsIgnoreCase(type)) {
                if ("DEPOSIT".equalsIgnoreCase(type)) {
                    predicates.add(cb.greaterThan(root.get("amount"), 0));
                } else if ("USAGE".equalsIgnoreCase(type)) {
                    predicates.add(cb.lessThan(root.get("amount"), 0));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Transaction> transactionPage = transactionRepo.findAll(spec, pageable);

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Yêu cầu quyền Quản trị viên."));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<PaymentLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(23, 59, 59)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), likeKeyword),
                        cb.like(cb.lower(root.get("transactionId")), likeKeyword),
                        cb.like(cb.lower(root.get("content")), likeKeyword)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PaymentLog> logPage = paymentLogRepo.findAll(spec, pageable);

        return ResponseEntity.ok(logPage);
    }
}
