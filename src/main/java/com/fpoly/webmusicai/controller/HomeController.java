package com.fpoly.webmusicai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HomeController {

    @GetMapping("/song/{id}")
    public String songDetail(@PathVariable("id") Integer id) {
        return "song-detail";
    }
}
