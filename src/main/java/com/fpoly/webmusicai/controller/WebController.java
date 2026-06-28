package com.fpoly.webmusicai.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.repository.SongRepository;

@Controller
public class WebController {

    // 1. Gọi Repository để có công cụ truy vấn Database
    @Autowired
    private SongRepository songRepo;

    @GetMapping("/")
    public String index(Model model) {
        // 2. Lấy danh sách nhạc public từ Database
        List<Song> publicSongs = songRepo.findByIsPublicTrueOrderByCreatedAtDesc();
        
        // 3. Nạp dữ liệu vào Model để truyền ra ngoài file HTML với tên là "publicSongs"
        model.addAttribute("publicSongs", publicSongs);
        
        return "index"; // Trả về file templates/index.html thông qua cổng 8080
    }

    @GetMapping("/login")
    public String login() {
        return "login"; // Nạp templates/login.html
    }

    @GetMapping("/register")
    public String register() {
        return "register"; // Nạp templates/register.html
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}