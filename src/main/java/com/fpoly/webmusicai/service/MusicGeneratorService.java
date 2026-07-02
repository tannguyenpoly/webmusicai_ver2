package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class MusicGeneratorService {

    @Value("${colab.music-api.url}")
    private String colabApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Hàm sinh nhạc chính từ Google Colab AI
     * @param prompt Đoạn mô tả bài hát
     * @return Map chứa thông tin Title và chuỗi Base64 để Front-end phát nhạc
     */
    public Map<String, Object> generateMusic(String prompt) {
        if (colabApiUrl == null || colabApiUrl.isEmpty()) {
            throw new RuntimeException("Chưa cấu hình link 'colab.music-api.url' trong application.properties!");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Gửi dữ liệu theo đúng cấu hình PromptRequest của Python FastAPI trên Colab
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Đang gửi yêu cầu sinh nhạc sang GPU Colab... Prompt: {}", prompt);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(colabApiUrl, entity, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] audioBytes = response.getBody();
                log.info("AI Colab đã tạo nhạc thành công! Kích thước: {} bytes", audioBytes.length);

                // Chuyển mảng byte thành chuỗi Base64 để thẻ <audio> ở Front-end đọc được ngay
                String base64Audio = java.util.Base64.getEncoder().encodeToString(audioBytes);

                Map<String, Object> result = new HashMap<>();
                result.put("title", "AI Generated - " + prompt);
                result.put("audio_url", "data:audio/wav;base64," + base64Audio);
                result.put("state", "succeeded");

                return result;
            } else {
                throw new RuntimeException("Server AI Colab trả về lỗi: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Lỗi kết nối tới Server AI Google Colab: {}", e.getMessage());
            throw new RuntimeException("Không thể kết nối tới lõi xử lý AI cá nhân trên Google Colab. Hãy chắc chắn bạn đã bật Colab.");
        }
    }
}