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
			// Format price and date
			NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
			String formattedPrice = currencyFormatter.format(order.getTotalPrice());
			SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
			String formattedDate = dateFormatter.format(order.getCreatedAt());

			// Generate PDF
			// Replace "₫" with "VND" for PDF compatibility if necessary
			String safePrice = formattedPrice.replace("₫", "VND");

			// Generate PDF
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Document document = new Document();
			PdfWriter.getInstance(document, baos);
			document.open();
			
			// Fonts
			com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 24, new java.awt.Color(34, 197, 94));
			com.lowagie.text.Font subTitleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 12, java.awt.Color.GRAY);
			com.lowagie.text.Font headerFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 12, java.awt.Color.WHITE);
			com.lowagie.text.Font normalFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA, 12, java.awt.Color.BLACK);
			com.lowagie.text.Font boldFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 12, java.awt.Color.BLACK);

			// Title
			Paragraph title = new Paragraph("WEBMUSICAI", titleFont);
			title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
			document.add(title);
			
			Paragraph subTitle = new Paragraph("INVOICE RECEIPT", subTitleFont);
			subTitle.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
			subTitle.setSpacingAfter(30);
			document.add(subTitle);

			// Order Info
			com.lowagie.text.pdf.PdfPTable infoTable = new com.lowagie.text.pdf.PdfPTable(2);
			infoTable.setWidthPercentage(100);
			infoTable.setSpacingAfter(20);
			
			com.lowagie.text.pdf.PdfPCell leftCell = new com.lowagie.text.pdf.PdfPCell();
			leftCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
			leftCell.addElement(new Paragraph("Order Code: " + order.getOrderCode(), boldFont));
			leftCell.addElement(new Paragraph("Date: " + formattedDate, normalFont));
			
			com.lowagie.text.pdf.PdfPCell rightCell = new com.lowagie.text.pdf.PdfPCell();
			rightCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
			Paragraph statusP = new Paragraph("Status: PAID SUCCESS", boldFont);
			statusP.setAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
			rightCell.addElement(statusP);
			
			infoTable.addCell(leftCell);
			infoTable.addCell(rightCell);
			document.add(infoTable);

			// Items Table
			com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(3);
			table.setWidthPercentage(100);
			table.setSpacingBefore(10f);
			table.setSpacingAfter(30f);

			// Table Header
			java.awt.Color headerColor = new java.awt.Color(34, 197, 94); // Green
			String[] headers = {"Description", "Credit Received", "Amount"};
			for (String h : headers) {
				com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(h, headerFont));
				cell.setBackgroundColor(headerColor);
				cell.setPadding(10);
				cell.setBorderColor(java.awt.Color.WHITE);
				table.addCell(cell);
			}
			
			// Table row
			java.awt.Color rowColor = new java.awt.Color(245, 245, 245);
			
			com.lowagie.text.pdf.PdfPCell descCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase("Package: " + order.getPkg().getTierCode(), normalFont));
			descCell.setPadding(10);
			descCell.setBackgroundColor(rowColor);
			descCell.setBorderColor(java.awt.Color.WHITE);
			table.addCell(descCell);
			
			com.lowagie.text.pdf.PdfPCell creditCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase("+" + order.getPkg().getTokens(), normalFont));
			creditCell.setPadding(10);
			creditCell.setBackgroundColor(rowColor);
			creditCell.setBorderColor(java.awt.Color.WHITE);
			table.addCell(creditCell);
			
			com.lowagie.text.pdf.PdfPCell priceCell = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(safePrice, boldFont));
			priceCell.setPadding(10);
			priceCell.setBackgroundColor(rowColor);
			priceCell.setBorderColor(java.awt.Color.WHITE);
			table.addCell(priceCell);
			
			document.add(table);
			
			// Total Section
			Paragraph totalPara = new Paragraph("Total: " + safePrice, com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 14, java.awt.Color.BLACK));
			totalPara.setAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
			document.add(totalPara);
			
			// Footer
			Paragraph footer = new Paragraph("Thank you for your business!", com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_OBLIQUE, 12, java.awt.Color.GRAY));
			footer.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
			footer.setSpacingBefore(50);
			document.add(footer);
			
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
