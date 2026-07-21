package com.fpoly.webmusicai.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.*;
import com.fpoly.webmusicai.config.VNPayConfig;
import com.fpoly.webmusicai.service.MailService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin("*")
@RestController
@RequestMapping("/api/orders")
public class OrderRestController {

    @Autowired OrderRepository orderRepo;
    @Autowired PackageRepository packageRepo;
    @Autowired UserRepository userRepo;
    @Autowired TransactionRepository transRepo;
    @Autowired MailService mailService;

    // Thông tin SePay Sandbox
    private final String SEPAY_MERCHANT_ID = "SP-LIVE-TT2A9773";
    private final String SEPAY_SECRET_KEY = "spsk_live_1VizS2oHJC6cp7nL1fepArAoHPDipDn2";

    // URL từ Cloudflare Tunnel
    private final String BASE_URL = "https://technical-tray-cannon-sum.trycloudflare.com/api/orders/sepay-ipn";

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, HttpServletRequest req) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Integer packageId = (Integer) body.get("package_id");
        String paymentMethod = (String) body.getOrDefault("payment_method", "VNPAY");

        Package pkg = packageRepo.findById(packageId).orElse(null);
        if (pkg == null) return ResponseEntity.badRequest().body("Gói không tồn tại!");
        User user = userRepo.findById(username).orElseThrow();

        String orderCode = (paymentMethod.equals("SEPAY") ? "SP" : "VN") + System.currentTimeMillis();

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setTotalPrice(pkg.getPrice());
        order.setStatus("PENDING");
        order.setUser(user);
        order.setPkg(pkg);
        orderRepo.save(order);

        if ("SEPAY".equals(paymentMethod)) {
            Map<String, String> params = new TreeMap<>();
            params.put("merchant_id", SEPAY_MERCHANT_ID);
            params.put("order_invoice_number", orderCode);
            params.put("order_amount", String.valueOf(pkg.getPrice()));
            params.put("currency", "VND");
            params.put("order_description", "Thanh toan don hang " + orderCode);
            params.put("success_url", BASE_URL + "/orders?status=success");
            params.put("error_url", BASE_URL + "/orders?status=error");
            params.put("cancel_url", BASE_URL + "/orders?status=cancel");

            String signature = generateSePaySignature(params, SEPAY_SECRET_KEY);
            params.put("signature", signature);
            return ResponseEntity.ok(params);
        } else {
            long amount = pkg.getPrice() * 100L;
            String vnp_IpAddr = VNPayConfig.getIpAddress(req);
            Map<String, String> vnp_Params = new HashMap<>();
            vnp_Params.put("vnp_Version", "2.1.0");
            vnp_Params.put("vnp_Command", "pay");
            vnp_Params.put("vnp_TmnCode", VNPayConfig.vnp_TmnCode);
            vnp_Params.put("vnp_Amount", String.valueOf(amount));
            vnp_Params.put("vnp_CurrCode", "VND");
            vnp_Params.put("vnp_TxnRef", orderCode);
            vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang: " + orderCode);
            vnp_Params.put("vnp_OrderType", "other");
            vnp_Params.put("vnp_Locale", "vn");
            vnp_Params.put("vnp_ReturnUrl", VNPayConfig.vnp_ReturnUrl);
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
            String vnp_SecureHash = VNPayConfig.hmacSHA512(VNPayConfig.secretKey, hashData.toString());
            String paymentUrl = VNPayConfig.vnp_PayUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
            return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
        }
    }

    @PostMapping("/sepay-ipn")
    public ResponseEntity<?> handleSePayIPN(@RequestParam Map<String, String> params) {
        String receivedSignature = params.get("signature");
        Map<String, String> checkParams = new TreeMap<>(params);
        checkParams.remove("signature");

        try {
            String calculatedSignature = generateSePaySignature(checkParams, SEPAY_SECRET_KEY);
            if (calculatedSignature.equals(receivedSignature)) {
                String orderCode = params.get("order_invoice_number");
                Order order = orderRepo.findByOrderCode(orderCode).orElse(null);
                if (order != null && "PENDING".equals(order.getStatus()) && "success".equals(params.get("status"))) {
                    processOrderSuccess(order, "SePay");
                    return ResponseEntity.ok("OK");
                }
            }
        } catch (Exception e) {
            log.error("Lỗi xác thực SePay IPN", e);
        }
        return ResponseEntity.ok("FAIL");
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

        if (VNPayConfig.hmacSHA512(VNPayConfig.secretKey, hashData.toString()).equals(vnp_SecureHash)) {
            Order order = orderRepo.findByOrderCode(request.getParameter("vnp_TxnRef")).orElse(null);
            if (order != null && "PENDING".equals(order.getStatus())) {
                if ("00".equals(request.getParameter("vnp_ResponseCode"))) {
                    processOrderSuccess(order, "VNPAY");
                    response.sendRedirect("/orders?status=success");
                    return;
                }
                order.setStatus("FAILED");
                orderRepo.save(order);
            }
        }
        response.sendRedirect("/orders?status=failed");
    }

    private void processOrderSuccess(Order order, String method) {
        order.setStatus("SUCCESS");
        orderRepo.save(order);
        User user = order.getUser();
        user.setTokenBalance(user.getTokenBalance() + order.getPkg().getTokens());
        if (order.getPkg().getId() == 2 || order.getPkg().getId() == 3) {
            user.setAccountTier("PRO");
            Calendar cal = Calendar.getInstance();
            if (user.getProExpiredAt() != null && user.getProExpiredAt().after(new Date())) cal.setTime(user.getProExpiredAt());
            cal.add(Calendar.DAY_OF_MONTH, 30);
            user.setProExpiredAt(cal.getTime());
        }
        userRepo.save(user);
        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(order.getPkg().getTokens());
        trans.setDescription("Thanh toán thành công qua " + method + " - Mã: " + order.getOrderCode());
        transRepo.save(trans);
        mailService.sendInvoiceEmail(user, order);
    }

    private String generateSePaySignature(Map<String, String> data, String secret) throws Exception {
        String payload = data.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders() {
        return ResponseEntity.ok(orderRepo.findByUserUsernameOrderByCreatedAtDesc(SecurityContextHolder.getContext().getAuthentication().getName()));
    }
}