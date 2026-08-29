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

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;

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
                            + username + "\n\n" + "Bạn nhận được 15 Credit miễn phí để bắt đầu tạo nhạc AI.\n\n"
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
			// Format price and date
			NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
			String formattedPrice = currencyFormatter.format(order.getTotalPrice());
			SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
			String formattedDate = dateFormatter.format(order.getCreatedAt());

			// Generate PDF
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Document document = new Document();
			PdfWriter.getInstance(document, baos);
			document.open();
			
			// Using basic text to avoid Vietnamese font issues in default PDF font
			document.add(new Paragraph("HOA DON THANH TOAN WEBMUSICAI"));
			document.add(new Paragraph("----------------------------------------"));
			document.add(new Paragraph("Ma don hang: " + order.getOrderCode()));
			document.add(new Paragraph("Ngay tao: " + formattedDate));
			document.add(new Paragraph("Ten goi: " + order.getPkg().getTierCode()));
			document.add(new Paragraph("Số Credit: +" + order.getPkg().getTokens()));
			document.add(new Paragraph("Tong tien: " + formattedPrice));
			document.add(new Paragraph("Trang thai: THANH TOAN THANH CONG"));
			document.add(new Paragraph("----------------------------------------"));
			document.add(new Paragraph("Cam on ban da su dung dich vu cua chung toi."));
			document.close();

			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			helper.setFrom(fromEmail);
			helper.setTo(user.getEmail());
			helper.setSubject("🎉 Chúc mừng! Thanh toán thành công đơn #" + order.getOrderCode());

			String text = String.format(
					"Xin chào %s,\n\n" +
							"Cảm ơn bạn đã mua hàng tại WebMusicAI. Hóa đơn chi tiết của bạn đã được đính kèm ở định dạng PDF trong email này.\n\n" +
							"Số credit đã được cộng vào tài khoản của bạn.\n\n" +
							"Trân trọng,\n" +
							"Đội ngũ WebMusicAI",
					user.getFullname());

			helper.setText(text);
			helper.addAttachment("HoaDon_" + order.getOrderCode() + ".pdf", new ByteArrayResource(baos.toByteArray()));

			mailSender.send(message);
			log.info("Đã gửi email hóa đơn (đính kèm PDF) cho đơn hàng {} tới: {}", order.getOrderCode(), user.getEmail());

		} catch (Throwable e) {
			log.error("Lỗi gửi email hóa đơn cho {} tới {}: {}", order.getOrderCode(), user.getEmail(), e.getMessage(), e);
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
