package com.fpoly.webmusicai.service;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

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
							+ username + "\n\n" + "Bạn nhận được 15 token miễn phí để bắt đầu tạo nhạc AI.\n\n"
							+ "Trân trọng,\n" + "Chúc bạn dùng ứng dụng vui vẻ");

			mailSender.send(message);
			log.info("Đã gửi email chào mừng tới: {}", toEmail);

		} catch (Exception e) {
			log.error("Lỗi gửi email tới {}: {}", toEmail, e.getMessage());
		}
	}

	@Async
	public void sendInvoiceEmail(User user, Order order) {
		if (user.getEmail() == null || user.getEmail().isEmpty()) {
			log.warn("Không thể gửi hóa đơn cho user '{}' vì không có email.", user.getUsername());
			return;
		}

		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromEmail);
			message.setTo(user.getEmail());
			message.setSubject("🎉 Chúc mừng! Thanh toán thành công đơn #" + order.getOrderCode());

			// Format price and date for email body
			NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
			String formattedPrice = currencyFormatter.format(order.getTotalPrice());
			SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
			String formattedDate = dateFormatter.format(order.getCreatedAt());

			String text = String.format(
					"Xin chào %s,\n\n" +
							"Cảm ơn bạn đã mua hàng tại WebMusicAI. Dưới đây là chi tiết hóa đơn của bạn:\n\n" +
							"----------------------------------------\n" +
							"Mã đơn hàng: %s\n" +
							"Ngày tạo: %s\n" +
							"Tên gói: %s\n" +
							"Số token: +%d\n" +
							"Tổng tiền: %s\n" +
							"Trạng thái: THANH TOÁN THÀNH CÔNG\n" +
							"----------------------------------------\n\n" +
							"Số token đã được cộng vào tài khoản của bạn.\n\n" +
							"Trân trọng,\n" +
							"Đội ngũ WebMusicAI",
					user.getFullname(), order.getOrderCode(), formattedDate, order.getPkg().getName(),
					order.getPkg().getTokens(), formattedPrice);

			message.setText(text);
			mailSender.send(message);
			log.info("Đã gửi email hóa đơn cho đơn hàng {} tới: {}", order.getOrderCode(), user.getEmail());

		} catch (Exception e) {
			log.error("Lỗi gửi email hóa đơn cho {} tới {}: {}", order.getOrderCode(), user.getEmail(), e.getMessage());
		}
	}

	@Async
	public void sendResetPasswordOtp(String toEmail, String otp) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromEmail);
			message.setTo(toEmail);
			message.setSubject("Yêu cầu đặt lại mật khẩu WebMusicAI");
			message.setText(
					"Xin chào,\n\n" +
							"Bạn đã yêu cầu đặt lại mật khẩu. Mã xác nhận của bạn là:\n\n" +
							"OTP: " + otp + "\n\n" +
							"Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.\n\n" +
							"Nếu bạn không yêu cầu điều này, vui lòng bỏ qua email này.\n\n" +
							"Trân trọng,\n" +
							"Đội ngũ WebMusicAI");

			mailSender.send(message);
			log.info("Đã gửi email OTP đặt lại mật khẩu tới: {}", toEmail);

		} catch (Exception e) {
			log.error("Lỗi gửi email OTP tới {}: {}", toEmail, e.getMessage());
		}
	}
}
