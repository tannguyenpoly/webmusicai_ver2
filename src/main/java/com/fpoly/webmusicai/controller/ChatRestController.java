package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.webmusicai.entity.ChatMessage;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.ChatMessageRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    @Autowired
    private ChatMessageRepository chatMessageRepo;

    @Autowired
    private UserRepository userRepo;

    // Lấy lịch sử trò chuyện giữa người dùng hiện tại và đối tác
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(@RequestParam String partner, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập"));
        }
        String currentUser = principal.getName();

        List<ChatMessage> history = chatMessageRepo
                .findBySenderUsernameAndRecipientUsernameOrSenderUsernameAndRecipientUsernameOrderByTimestampAsc(
                        currentUser, partner, partner, currentUser);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("sender", msg.getSender().getUsername());
            map.put("recipient", msg.getRecipient().getUsername());
            map.put("content", msg.getContent());
            map.put("timestamp", msg.getTimestamp());
            map.put("isRead", msg.getIsRead());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    // Lấy danh sách liên hệ nhắn tin gần đây kèm tin nhắn cuối và số lượng chưa đọc
    @GetMapping("/recent-chats")
    public ResponseEntity<?> getRecentChats(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập"));
        }
        String currentUser = principal.getName();

        List<ChatMessage> lastMessages = chatMessageRepo.findLastMessagesForRecentChats(currentUser);
        List<Map<String, Object>> recentChats = new ArrayList<>();

        for (ChatMessage msg : lastMessages) {
            // Xác định ai là đối tác chat trong cuộc trò chuyện này
            User partner = msg.getSender().getUsername().equals(currentUser) 
                    ? msg.getRecipient() 
                    : msg.getSender();

            long unreadCount = chatMessageRepo
                    .countBySenderUsernameAndRecipientUsernameAndIsReadFalse(partner.getUsername(), currentUser);

            Map<String, Object> chatInfo = new HashMap<>();
            chatInfo.put("username", partner.getUsername());
            chatInfo.put("fullname", partner.getFullname());
            
            // Xử lý avatar dự phòng giống AuthController/UserRestController
            String photo = partner.getPhoto();
            if (photo == null || photo.trim().isEmpty()) {
                photo = "https://ui-avatars.com/api/?name=" 
                        + java.net.URLEncoder.encode(partner.getFullname(), java.nio.charset.StandardCharsets.UTF_8) 
                        + "&background=16a34a&color=fff&rounded=true";
            }
            chatInfo.put("photo", photo);
            chatInfo.put("lastMessage", msg.getContent());
            chatInfo.put("lastMessageSender", msg.getSender().getUsername());
            chatInfo.put("timestamp", msg.getTimestamp());
            chatInfo.put("unreadCount", unreadCount);

            recentChats.add(chatInfo);
        }

        return ResponseEntity.ok(recentChats);
    }

    // Đánh dấu tất cả tin nhắn từ đối tác gửi cho người dùng hiện tại là ĐÃ ĐỌC
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/messages/read-all")
    public ResponseEntity<?> markAllAsRead(@RequestParam String partner, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập"));
        }
        String currentUser = principal.getName();

        List<ChatMessage> history = chatMessageRepo
                .findBySenderUsernameAndRecipientUsernameOrSenderUsernameAndRecipientUsernameOrderByTimestampAsc(
                        partner, currentUser, partner, currentUser);

        boolean updated = false;
        for (ChatMessage msg : history) {
            if (msg.getRecipient().getUsername().equals(currentUser) && !msg.getIsRead()) {
                msg.setIsRead(true);
                updated = true;
            }
        }

        if (updated) {
            chatMessageRepo.saveAll(history);
        }

        return ResponseEntity.ok(Map.of("message", "Đã đánh dấu đã đọc tất cả tin nhắn"));
    }

    // Lấy tổng số lượng tin nhắn chưa đọc của người dùng hiện tại
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập"));
        }
        String currentUser = principal.getName();

        long totalUnread = chatMessageRepo.countByRecipientUsernameAndIsReadFalse(currentUser);
        return ResponseEntity.ok(Map.of("unreadCount", totalUnread));
    }

    // Tìm kiếm người dùng khác trong hệ thống để nhắn tin mới
    @GetMapping("/search-users")
    public ResponseEntity<?> searchUsers(@RequestParam String query, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập"));
        }
        String currentUser = principal.getName();

        // Giới hạn tìm kiếm 10 kết quả
        Page<User> usersPage = userRepo
                .findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(query, query, PageRequest.of(0, 10));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : usersPage.getContent()) {
            // Không hiển thị chính mình trong kết quả tìm kiếm chat
            if (u.getUsername().equals(currentUser)) {
                continue;
            }

            Map<String, Object> map = new HashMap<>();
            map.put("username", u.getUsername());
            map.put("fullname", u.getFullname());

            String photo = u.getPhoto();
            if (photo == null || photo.trim().isEmpty()) {
                photo = "https://ui-avatars.com/api/?name=" 
                        + java.net.URLEncoder.encode(u.getFullname(), java.nio.charset.StandardCharsets.UTF_8) 
                        + "&background=16a34a&color=fff&rounded=true";
            }
            map.put("photo", photo);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}
