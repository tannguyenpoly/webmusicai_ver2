package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.UserRepository;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authManager; // Tiêm bộ quản lý xác thực của Spring Security

    @Autowired
    UserRepository userRepo;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        try {
            String username = loginData.get("username");
            String password = loginData.get("password");

            // 1. Chuyển username/password thành phôi xác thực của Spring Security
            // Lệnh này sẽ kích hoạt CustomUserDetailsService chui vào DB rà quét ngầm
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            // 2. Nếu không có ngoại lệ (Exception) xảy ra -> Đăng nhập thành công!
            // Lưu thông tin đăng nhập vào ngữ cảnh hệ thống
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 3. Lấy thông tin User thật từ DB ra để trả về cho Client hiển thị lên giao diện
            User user = userRepo.findById(username).get();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Đăng nhập qua Spring Security chuẩn thành công!");
            response.put("username", user.getUsername());
            response.put("fullname", user.getFullname());
            response.put("token_balance", user.getTokenBalance());
            response.put("token", "fake-jwt-token-123456"); // Sẽ thay bằng JWT thật ở bài sau

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            // Nếu sai mật khẩu hoặc tài khoản bị khóa (enabled = 0), Spring Security sẽ ném lỗi vào đây
            return ResponseEntity.status(401).body("Sai tài khoản hoặc mật khẩu thật từ Database!");
        }
    }
}