package com.fpoly.webmusicai.service;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Tìm user trong Database
        com.fpoly.webmusicai.entity.User user = userRepo.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user!"));

        // Chuyển đổi danh sách quyền (Roles) từ Database sang định dạng của Spring Security
        String[] roles = user.getAuthorities().stream()
                .map(auth -> auth.getRole().getId())
                .collect(Collectors.toList())
                .toArray(new String[0]);

        // Trả về UserDetails cho Spring Security
        return User.withUsername(user.getUsername())
                .password(user.getPassword()) // Lưu ý: pass phải có {noop} hoặc đã được mã hóa
                .roles(roles)
                .build();
    }
}