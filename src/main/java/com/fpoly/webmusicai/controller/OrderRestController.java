package com.fpoly.webmusicai.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.*;
import com.fpoly.webmusicai.config.VNPayConfig;
import com.fpoly.webmusicai.service.PaymentService;
import com.fpoly.webmusicai.service.OrderLifecycleService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    @Autowired OrderRepository orderRepo;
    @Autowired PackageRepository packageRepo;
    @Autowired UserRepository userRepo;
    @Autowired PaymentService paymentService;
    @Autowired OrderLifecycleService orderLifecycleService;
    @Autowired VNPayConfig vnpayConfig;

    @Value("${sepay.bank.code:MB}")
    private String bankCode;

    @Value("${sepay.bank.account-number:}")
    private String bankAccountNumber;

    @Value("${sepay.bank.account-name:}")
    private String bankAccountName;

    @Value("${sepay.webhook.api-key:}")
    private String sepayWebhookApiKey;

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, HttpServletRequest req) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Object packageIdValue = body.get("package_id");
        if (!(packageIdValue instanceof Number packageNumber)) {
            return ResponseEntity.badRequest().body(Map.of("message", "package_id không hợp lệ"));
        }
        Integer packageId = packageNumber.intValue();
        String paymentMethod = String.valueOf(body.getOrDefault("payment_method", "VNPAY"))
                .toUpperCase(Locale.ROOT);
        if (!Set.of("SEPAY", "VNPAY").contains(paymentMethod)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phương thức thanh toán không được hỗ trợ"));
        }

        Package pkg = packageRepo.findById(packageId).orElse(null);
        if (pkg == null) return ResponseEntity.badRequest().body("Gói không tồn tại!");
        User user = userRepo.findById(username).orElseThrow();

        // Mỗi người chỉ duy trì một yêu cầu chờ thanh toán. Đơn cũ chưa xử lý
        // được đóng lại để không treo mãi trong lịch sử.
        for (Order existing : orderRepo.findByUserUsernameOrderByCreatedAtDesc(username)) {
            if ("PENDING".equals(existing.getStatus())) {
                if (!orderLifecycleService.expireIfNeeded(existing)) {
                    existing.setStatus("CANCELLED");
                    orderRepo.save(existing);
                }
            }
        }

        String orderCode = paymentMethod.equals("SEPAY")
                ? generateSepayOrderCode()
                : "VN" + System.currentTimeMillis()
                        + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setTotalPrice(pkg.getPrice());
        order.setStatus("PENDING");
        order.setPaymentMethod(paymentMethod);
        order.setUser(user);
        order.setPkg(pkg);
        orderRepo.save(order);

        if ("SEPAY".equals(paymentMethod)) {
            if (bankAccountNumber == null || bankAccountNumber.isBlank()
                    || sepayWebhookApiKey == null || sepayWebhookApiKey.isBlank()) {
                order.setStatus("FAILED");
                orderRepo.save(order);
                return ResponseEntity.status(503).body(Map.of(
                        "message", "SePay chưa được cấu hình số tài khoản và API key webhook"));
            }
            String qrUrl = "https://vietqr.app/img?acc="
                    + URLEncoder.encode(bankAccountNumber, StandardCharsets.UTF_8)
                    + "&bank=" + URLEncoder.encode(bankCode, StandardCharsets.UTF_8)
                    + "&amount=" + pkg.getPrice()
                    + "&des=" + URLEncoder.encode(orderCode, StandardCharsets.UTF_8)
                    + "&template=compact&showinfo=true&holder="
                    + URLEncoder.encode(bankAccountName, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of(
                    "order_invoice_number", orderCode,
                    "amount", pkg.getPrice(),
                    "qrUrl", qrUrl));
        } else {
            if (!vnpayConfig.isConfigured()) {
                order.setStatus("FAILED");
                orderRepo.save(order);
                return ResponseEntity.status(503).body(Map.of(
                        "message", "VNPay sandbox chưa được cấu hình"));
            }
            long amount = pkg.getPrice() * 100L;
            String vnp_IpAddr = vnpayConfig.getIpAddress(req);
            Map<String, String> vnp_Params = new HashMap<>();
            vnp_Params.put("vnp_Version", "2.1.0");
            vnp_Params.put("vnp_Command", "pay");
            vnp_Params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
            vnp_Params.put("vnp_Amount", String.valueOf(amount));
            vnp_Params.put("vnp_CurrCode", "VND");
            vnp_Params.put("vnp_TxnRef", orderCode);
            vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang: " + orderCode);
            vnp_Params.put("vnp_OrderType", "other");
            vnp_Params.put("vnp_Locale", "vn");
            vnp_Params.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
            vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

            Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
            cld.add(Calendar.MINUTE, 15);
            vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

            List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
            Collections.sort(fieldNames);
            StringBuilder hashData = new StringBuilder();
            StringBuilder query = new StringBuilder();
            for (String fieldName : fieldNames) {
                String fieldValue = vnp_Params.get(fieldName);
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString())).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (!fieldName.equals(fieldNames.get(fieldNames.size() - 1))) {
                    hashData.append('&');
                    query.append('&');
                }
            }
            String vnp_SecureHash = vnpayConfig.hmacSHA512(vnpayConfig.getSecretKey(), hashData.toString());
            String paymentUrl = vnpayConfig.getPayUrl() + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
            return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
        }
    }

    @PostMapping("/sepay-ipn")
    public ResponseEntity<?> handleSePayIPN(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> payload) {
        if (sepayWebhookApiKey != null && !sepayWebhookApiKey.isBlank()
                && !("Apikey " + sepayWebhookApiKey).equals(authorization)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "API key không hợp lệ"));
        }
        if (sepayWebhookApiKey == null || sepayWebhookApiKey.isBlank()) {
            return ResponseEntity.status(503)
                    .body(Map.of("success", false, "message", "Webhook SePay chưa được cấu hình API key"));
        }
        try {
            String transferType = String.valueOf(payload.getOrDefault("transferType", ""));
            String content = String.valueOf(payload.getOrDefault("content", ""));
            if (!"in".equalsIgnoreCase(transferType)) {
                return ResponseEntity.ok(Map.of("success", true));
            }

            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\bSP\\d+\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(content);
            if (!matcher.find()) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Không có mã đơn hàng"));
            }

            String orderCode = matcher.group().toUpperCase(Locale.ROOT);
            Object amountValue = payload.get("transferAmount");
            int amount = amountValue instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(amountValue));
            String transactionId = String.valueOf(
                    payload.getOrDefault("id", payload.getOrDefault("referenceCode", "")));

            paymentService.complete(orderCode, "SEPAY", transactionId, amount, payload.toString());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Lỗi xác thực SePay IPN", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/vnpay-return")
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> fields = new HashMap<>();
        request.getParameterNames().asIterator().forEachRemaining(name -> fields.put(name, request.getParameter(name)));
        String vnp_SecureHash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            hashData.append(fieldName).append('=').append(URLEncoder.encode(fields.get(fieldName), StandardCharsets.US_ASCII.toString()));
            if (!fieldName.equals(fieldNames.get(fieldNames.size() - 1))) hashData.append('&');
        }

        if (vnpayConfig.isConfigured()
                && vnpayConfig.hmacSHA512(vnpayConfig.getSecretKey(), hashData.toString()).equals(vnp_SecureHash)) {
            Order order = orderRepo.findByOrderCode(request.getParameter("vnp_TxnRef")).orElse(null);
            if (order != null && "PENDING".equals(order.getStatus())) {
                if ("00".equals(request.getParameter("vnp_ResponseCode"))) {
                    String transactionId = request.getParameter("vnp_TransactionNo");
                    int receivedAmount = Integer.parseInt(request.getParameter("vnp_Amount")) / 100;
                    paymentService.complete(
                            order.getOrderCode(), "VNPAY", transactionId,
                            receivedAmount, fields.toString());
                    response.sendRedirect("/orders?status=success");
                    return;
                }
                order.setStatus("FAILED");
                orderRepo.save(order);
            }
        }
        response.sendRedirect("/orders?status=failed");
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders() {
        return ResponseEntity.ok(orderRepo.findByUserUsernameAndStatusOrderByCreatedAtDesc(
                SecurityContextHolder.getContext().getAuthentication().getName(), "SUCCESS"));
    }

    @PostMapping("/{orderCode}/cancel")
    @Transactional
    public ResponseEntity<?> cancelOrder(@PathVariable String orderCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Order order = orderRepo.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!order.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("message", "Không có quyền hủy đơn hàng"));
        }
        if ("SUCCESS".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Đơn hàng đã thanh toán thành công nên không thể hủy"));
        }
        if (!"PENDING".equals(order.getStatus())) {
            return ResponseEntity.ok(Map.of(
                    "message", "Đơn hàng đã được đóng",
                    "status", order.getStatus()));
        }
        order.setStatus("CANCELLED");
        orderRepo.save(order);
        return ResponseEntity.ok(Map.of(
                "message", "Đã hủy yêu cầu thanh toán",
                "status", order.getStatus()));
    }

    @GetMapping("/{orderCode}/status")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderCode) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Order order = orderRepo.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!order.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("message", "Không có quyền xem đơn hàng"));
        }
        orderLifecycleService.expireIfNeeded(order);
        return ResponseEntity.ok(Map.of(
                "orderCode", order.getOrderCode(),
                "status", order.getStatus(),
                "remainingSeconds", orderLifecycleService.remainingSeconds(order)));
    }

    private String generateSepayOrderCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            long suffix = java.util.concurrent.ThreadLocalRandom.current()
                    .nextLong(1_000_000_000L, 10_000_000_000L);
            String candidate = "SP" + suffix;
            if (!orderRepo.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không thể tạo mã thanh toán duy nhất, vui lòng thử lại");
    }
}
