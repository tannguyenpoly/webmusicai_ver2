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

	@Autowired
	private SongRepository songRepo;

	@GetMapping("/")
	public String index(Model model) {
		List<Song> publicSongs = songRepo.findByIsPublicTrueOrderByCreatedAtDesc();

		model.addAttribute("publicSongs", publicSongs);

		return "index";
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	@GetMapping("/register")
	public String register() {
		return "register";
	}

	@GetMapping("/favorites")
	public String favorites() {
		return "favorites";
	}

	@GetMapping("/profile")
	public String profile() {
		return "profile";
	}

	@GetMapping("/admin")
	public String admin() {
		return "admin";
	}

	@GetMapping("/orders")
	public String orders() {
		return "orders";
	}
}