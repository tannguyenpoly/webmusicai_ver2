package com.fpoly.webmusicai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
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
}