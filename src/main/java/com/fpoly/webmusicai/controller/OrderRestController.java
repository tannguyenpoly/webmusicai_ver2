package com.fpoly.webmusicai.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest; // Dùng javax.servlet... nếu bạn xài Spring Boot 2.x
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.*;
import com.fpoly.webmusicai.config.VNPayConfig; // Import class cấu hình vừa tạo
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
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Integer> body, HttpServletRequest req) throws IOException {
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

        String vnp_TxnRef = "ORD_" + System.currentTimeMillis() + "_" + VNPayConfig.getRandomNumber(6);

        Order order = new Order();
        order.setOrderCode(vnp_TxnRef);
        order.setTotalPrice(pkg.getPrice());
        order.setStatus("PENDING");
        order.setUser(user);
        order.setPkg(pkg);
        orderRepo.save(order);

        // --- CẤU HÌNH CÁC THAM SỐ GỬI SANG VNPAY ---
        long amount = pkg.getPrice() * 100L; // VNPAY yêu cầu nhân 100
        String vnp_IpAddr = VNPayConfig.getIpAddress(req);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", VNPayConfig.vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang: " + vnp_TxnRef);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", VNPayConfig.vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Sắp xếp tham số và tạo hash data
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                // Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayConfig.hmacSHA512(VNPayConfig.secretKey, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = VNPayConfig.vnp_PayUrl + "?" + queryUrl;

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã tạo đơn hàng, chuyển hướng thanh toán!");
        response.put("paymentUrl", paymentUrl); // Trả URL về cho Frontend

        return ResponseEntity.ok(response);
    }

    // API này sẽ bắt kết quả trả về từ VNPAY qua phương thức GET
    @GetMapping("/vnpay-return")
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        if (fields.containsKey("vnp_SecureHashType")) {
            fields.remove("vnp_SecureHashType");
        }
        if (fields.containsKey("vnp_SecureHash")) {
            fields.remove("vnp_SecureHash");
        }

        // Tạo hash lại để đối chiếu chữ ký
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String signValue = VNPayConfig.hmacSHA512(VNPayConfig.secretKey, hashData.toString());

        // Kiểm tra chữ ký và trạng thái giao dịch
        if (signValue.equals(vnp_SecureHash)) {
            String orderCode = request.getParameter("vnp_TxnRef");
            Order order = orderRepo.findByOrderCode(orderCode).orElse(null);

            if (order != null && "PENDING".equals(order.getStatus())) {
                if ("00".equals(request.getParameter("vnp_ResponseCode"))) {
                    // THANH TOÁN THÀNH CÔNG -> Cập nhật Token
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
                    trans.setDescription("Thanh toán VNPAY thành công gói " + pkg.getName() + " - Mã: " + orderCode);
                    transRepo.save(trans);

                    response.sendRedirect("/orders?status=success");
                    return;
                } else {
                    // Giao dịch không thành công
                    order.setStatus("FAILED");
                    orderRepo.save(order);
                    response.sendRedirect("/orders?status=failed");
                    return;
                }
            }
        }

        // Chữ ký không hợp lệ hoặc lỗi
        response.sendRedirect("/orders?status=invalid");
    }


	@GetMapping("/my-orders")
	public ResponseEntity<?> getMyOrders() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		List<Order> orders = orderRepo.findByUserUsernameOrderByCreatedAtDesc(username);
		return ResponseEntity.ok(orders);
	}
}