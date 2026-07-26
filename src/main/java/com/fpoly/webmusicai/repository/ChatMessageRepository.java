package com.fpoly.webmusicai.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fpoly.webmusicai.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    // Lấy lịch sử chat giữa 2 người (sắp xếp tăng dần theo thời gian)
    List<ChatMessage> findBySenderUsernameAndRecipientUsernameOrSenderUsernameAndRecipientUsernameOrderByTimestampAsc(
        String sender1, String recipient1, String sender2, String recipient2
    );

    // Đếm số tin nhắn chưa đọc từ một người cụ thể gửi cho mình
    long countBySenderUsernameAndRecipientUsernameAndIsReadFalse(String sender, String recipient);

    // Đếm tổng số tin nhắn chưa đọc của một người nhận
    long countByRecipientUsernameAndIsReadFalse(String recipient);

    // Lấy danh sách tin nhắn cuối cùng của mỗi cuộc trò chuyện (để hiển thị danh sách chat gần đây)
    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM ChatMessage m2 WHERE m2.sender.username = :username OR m2.recipient.username = :username " +
           "  GROUP BY CASE WHEN m2.sender.username = :username THEN m2.recipient.username ELSE m2.sender.username END" +
           ") ORDER BY m.timestamp DESC")
    List<ChatMessage> findLastMessagesForRecentChats(@Param("username") String username);
}
