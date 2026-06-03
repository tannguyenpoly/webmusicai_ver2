package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

	@Autowired
	private JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String fromEmail;

	public void sendWelcomeEmail(String toEmail, String fullname, String username) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromEmail);
			message.setTo(toEmail);
			message.setSubject("🎵 Chào mừng bạn đến với WebMusicAI!");
			message.setText(
					"Xin chào " + fullname + ",\n\n" + "Tài khoản của bạn đã được tạo thành công!\n" + "Tên đăng nhập: "
							+ username + "\n\n" + "Bạn nhận được 5 token miễn phí để bắt đầu tạo nhạc AI.\n\n"
							+ "Trân trọng,\n" + "Chúc bạn dùng ứng dụng vui vẻ");

			mailSender.send(message);
			log.info("Đã gửi email chào mừng tới: {}", toEmail);

		} catch (Exception e) {
			// Không throw — lỗi email không nên làm hỏng luồng đăng ký
			log.error("Lỗi gửi email tới {}: {}", toEmail, e.getMessage());
		}
	}
}