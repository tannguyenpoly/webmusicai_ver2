package com.fpoly.webmusicai.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.*;
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
	public ResponseEntity<?> createOrder(@RequestBody Map<String, Integer> body) {
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

		String orderCode = "ORD_" + System.currentTimeMillis() + "_"
				+ UUID.randomUUID().toString().substring(0, 6).toUpperCase();

		Order order = new Order();
		order.setOrderCode(orderCode);
		order.setTotalPrice(pkg.getPrice());
		order.setStatus("PENDING");
		order.setUser(user);
		order.setPkg(pkg);
		orderRepo.save(order);

		log.info("Tạo order {} cho user {} - gói {} - {}đ", orderCode, username, pkg.getName(), pkg.getPrice());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "Đã tạo đơn hàng, vui lòng thanh toán!");
		response.put("order_code", orderCode);
		response.put("package_name", pkg.getName());
		response.put("tokens", pkg.getTokens());
		response.put("price", pkg.getPrice());
		response.put("status", "PENDING");

		return ResponseEntity.ok(response);
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