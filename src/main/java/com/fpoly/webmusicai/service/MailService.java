package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

@Service
@Slf4j
public class MailService {

	@Autowired
	private JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String fromEmail;

	@Async
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
			log.error("Lỗi gửi email tới {}: {}", toEmail, e.getMessage());
		}
	}

	@Async
	public void sendResetPasswordOtp(String toEmail, String otpCode) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromEmail);
			message.setTo(toEmail);
			message.setSubject(" Mã xác nhận khôi phục mật khẩu - WebMusicAI");
			message.setText(
					"Xin chào,\n\n"
							+ "Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản WebMusicAI của mình.\n"
							+ "Mã xác nhận (OTP) của bạn là: " + otpCode + "\n\n"
							+ "Mã xác nhận này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n"
							+ "Trân trọng,\n"
							+ "Đội ngũ WebMusicAI");

			mailSender.send(message);
			log.info("Đã gửi mã OTP đặt lại mật khẩu tới: {}", toEmail);

		} catch (Exception e) {
			log.error("Lỗi gửi email OTP tới {}: {}", toEmail, e.getMessage());
		}
	}
}