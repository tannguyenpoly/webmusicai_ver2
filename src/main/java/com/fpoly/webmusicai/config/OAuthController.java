package com.fpoly.webmusicai.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fpoly.webmusicai.entity.Authority;
import com.fpoly.webmusicai.entity.Role;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.AuthorityRepository;
import com.fpoly.webmusicai.repository.RoleRepository;
import com.fpoly.webmusicai.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthController implements AuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RoleRepository roleRepo;

    @Autowired
    private AuthorityRepository authorityRepo;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        if (email == null || email.trim().isEmpty()) {
            response.sendRedirect("/login?error=email_not_found");
            return;
        }

        Optional<User> optionalUser = userRepo.findFirstByEmail(email);
        User user;

        String displayName = name != null ? name : email;
        String avatarUrl = (picture != null && !picture.trim().isEmpty()) ? picture 
                : "https://ui-avatars.com/api/?name=" + URLEncoder.encode(displayName, StandardCharsets.UTF_8) + "&background=16a34a&color=fff&rounded=true";

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            user.setPhoto(avatarUrl);
            userRepo.save(user);
        } else {
            String username = email;
            if (userRepo.existsById(username)) {
                user = userRepo.findById(username).get();
                if (user.getPhoto() == null || user.getPhoto().trim().isEmpty()) {
                    user.setPhoto(avatarUrl);
                    userRepo.save(user);
                }
            } else {
                user = new User();
                user.setUsername(username);
                user.setEmail(email);
                user.setFullname(displayName);
                user.setPhoto(avatarUrl);
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                user.setTokenBalance(5);
                user.setEnabled(true);
                user.setAccountTier("BASIC");
                userRepo.save(user);

                Role role = roleRepo.findById("USER").orElse(null);
                if (role != null) {
                    Authority authority = new Authority();
                    authority.setUser(user);
                    authority.setRole(role);
                    authorityRepo.save(authority);
                }
            }
        }

        String token = jwtService.generateToken(user.getUsername());

        boolean isAdmin = user.getAuthorities() != null && user.getAuthorities().stream()
                .anyMatch(a -> a.getRole() != null && "ADMIN".equalsIgnoreCase(a.getRole().getId()));

        Cookie cookie = new Cookie("jwt_token", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(86400);
        response.addCookie(cookie);

        String redirectUrl = "/?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8)
                + "&isAdmin=" + isAdmin;

        response.sendRedirect(redirectUrl);
    }
}
