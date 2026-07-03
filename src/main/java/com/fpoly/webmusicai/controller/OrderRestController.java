package com.fpoly.webmusicai.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.*;
import com.fpoly.webmusicai.config.VnPayConfig; // Gọi file Config chạy được của bạn
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin("*")
@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    @Autowired
    OrderRepository orderRepo;
    @Autowired
    PackageRepository packageRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    TransactionRepository transRepo;

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Integer> body, HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Integer packageId = body.get("package_id");
        if (packageId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Thiếu package_id!"));
        }

        Package pkg = packageRepo.findById(packageId).orElse(null);
        if (pkg == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gói không tồn tại!"));
        }

        User user = userRepo.findById(username).orElseThrow();

        String orderCode = "ORD" + System.currentTimeMillis();

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setTotalPrice(pkg.getPrice());
        order.setStatus("PENDING");
        order.setUser(user);
        order.setPkg(pkg);
        orderRepo.save(order);

        log.info("Tạo order {} cho user {} - gói {} - {}đ", orderCode, username, pkg.getName(), pkg.getPrice());

        // --- CẤU HÌNH THAM SỐ THEO TÀI LIỆU VNPAY 2.1.0 POST METHOD ---
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        long amount = pkg.getPrice() * 100L;

        String vnp_TxnRef = orderCode;
        String vnp_IpAddr = VnPayConfig.getIpAddress(req);
        String vnp_TmnCode = VnPayConfig.vnp_TmnCode;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_BankCode", "");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang:" + vnp_TxnRef);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", "http://localhost:8080/api/orders/vnpay-callback");
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Format thời gian chuẩn GMT+7 theo tài liệu quy định
        java.time.ZonedDateTime nowGmt7 = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        String vnp_CreateDate = nowGmt7.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        String vnp_ExpireDate = nowGmt7.plusMinutes(15).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Sắp xếp các tham số theo thứ tự bảng chữ cái alphabet từ A-Z
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        try {
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = vnp_Params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    // 1. XÂY DỰNG CHUỖI HASH DATA (Dùng Raw Value thô - KHÔNG ĐƯỢC ENCODE URL)
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(fieldValue);

                    // 2. XÂY DỰNG CHUỖI QUERY STRING ĐỂ ĐÍNH KÈM TRÊN URL (Bắt buộc phải encode URL)
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Lỗi định dạng cấu trúc tham số"));
        }

        // Thực hiện băm dữ liệu HMAC-SHA512 dựa trên chuỗi Raw Data chuẩn tài liệu kỹ thuật
        String vnp_SecureHash = VnPayConfig.hmacSHA512(VnPayConfig.secretKey, hashData.toString());
        String paymentUrl = VnPayConfig.vnp_PayUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã khởi tạo hóa đơn giao dịch!");
        response.put("order_code", orderCode);
        response.put("payment_url", paymentUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay-callback")
    public ResponseEntity<Void> vnpayCallback(@RequestParam Map<String, String> queryParams, HttpServletRequest request) throws java.io.IOException {
        String vnp_SecureHash = queryParams.get("vnp_SecureHash");
        if (queryParams.containsKey("vnp_SecureHashType")) {
            queryParams.remove("vnp_SecureHashType");
        }
        queryParams.remove("vnp_SecureHash");

        List<String> fieldNames = new ArrayList<>(queryParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = queryParams.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Khi nhận callback kết quả trả về, VNPAY cũng yêu cầu dùng chuỗi thô để đối soát kiểm tra chữ ký
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(fieldValue);
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String signValue = VnPayConfig.hmacSHA512(VnPayConfig.secretKey, hashData.toString());
        String orderCode = queryParams.get("vnp_TxnRef");
        String responseCode = queryParams.get("vnp_ResponseCode");

        if (signValue.equalsIgnoreCase(vnp_SecureHash)) {
            if ("00".equals(responseCode)) {
                this.confirmPaymentInternal(orderCode, "SUCCESS");
            } else {
                this.confirmPaymentInternal(orderCode, "FAILED");
            }
        } else {
            log.error("Hệ thống phát hiện sai mã bảo mật đối soát chữ ký Checksum VNPAY!");
            this.confirmPaymentInternal(orderCode, "FAILED");
        }

        String redirectUrl = "http://localhost:8080/orders.html";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(redirectUrl))
                .build();
    }

    private void confirmPaymentInternal(String orderCode, String targetStatus) {
        Order order = orderRepo.findByOrderCode(orderCode).orElse(null);
        if (order == null || !"PENDING".equals(order.getStatus())) {
            return;
        }

        if ("FAILED".equals(targetStatus)) {
            order.setStatus("FAILED");
            orderRepo.save(order);
            return;
        }

        order.setStatus("SUCCESS");
        orderRepo.save(order);

        User user = order.getUser();
        Package pkg = order.getPkg();
        user.setTokenBalance(user.getTokenBalance() + pkg.getTokens());
        if (pkg.getId() == 2 || pkg.getId() == 3) {
            user.setAccountTier("PRO");

            java.util.Calendar cal = java.util.Calendar.getInstance();
            if (user.getProExpiredAt() != null && user.getProExpiredAt().after(new java.util.Date())) {
                cal.setTime(user.getProExpiredAt());
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, 30);
            user.setProExpiredAt(cal.getTime());
        }
        userRepo.save(user);

        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(pkg.getTokens());
        trans.setDescription("Nạp thành công gói " + pkg.getName() + " - Mã: " + orderCode);
        transRepo.save(trans);

        log.info("Xử lý thành công trạng thái VNPAY đơn hàng {}, cộng {} token cho {}", orderCode, pkg.getTokens(),
                user.getUsername());
    }

    @PostMapping("/confirm/{orderCode}")
    public ResponseEntity<?> confirmPayment(@PathVariable String orderCode) {
        Order order = orderRepo.findByOrderCode(orderCode).orElse(null);

        if (order == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Đơn hàng không tồn tại!"));
        }
        if ("SUCCESS".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Đơn hàng đã được xử lý trước đó!"));
        }

        order.setStatus("SUCCESS");
        orderRepo.save(order);

        User user = order.getUser();
        Package pkg = order.getPkg();
        user.setTokenBalance(user.getTokenBalance() + pkg.getTokens());
        if (pkg.getId() == 2 || pkg.getId() == 3) {
            user.setAccountTier("PRO");

            java.util.Calendar cal = java.util.Calendar.getInstance();
            if (user.getProExpiredAt() != null && user.getProExpiredAt().after(new java.util.Date())) {
                cal.setTime(user.getProExpiredAt());
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, 30);
            user.setProExpiredAt(cal.getTime());
        }
        userRepo.save(user);

        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(pkg.getTokens());
        trans.setDescription("Nạp thành công gói " + pkg.getName() + " - Mã: " + orderCode);
        transRepo.save(trans);

        log.info("Xác nhận thanh toán {} thành công, cộng {} token cho {}", orderCode, pkg.getTokens(),
                user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Thanh toán thành công!");
        response.put("order_code", orderCode);
        response.put("tokens_added", pkg.getTokens());
        response.put("new_balance", user.getTokenBalance());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        List<Order> orders = orderRepo.findByUserUsernameOrderByCreatedAtDesc(username);
        return ResponseEntity.ok(orders);
    }
}