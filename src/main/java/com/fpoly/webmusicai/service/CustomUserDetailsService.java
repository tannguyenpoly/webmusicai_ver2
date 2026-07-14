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
        com.fpoly.webmusicai.entity.User user = userRepo.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user!"));

        String[] roles = user.getAuthorities().stream()
                .map(auth -> auth.getRole().getId())
                .collect(Collectors.toList())
                .toArray(new String[0]);

        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .roles(roles)
                .disabled(!user.getEnabled())
                .build();
    }
}