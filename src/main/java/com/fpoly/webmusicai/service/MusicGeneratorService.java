package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
@Slf4j
public class MusicGeneratorService {

    @Value("${colab.music-api.url}")
    private String colabApiUrl;

    private final RestTemplate restTemplate;

    public MusicGeneratorService(
            @Value("${colab.music-api.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${colab.music-api.read-timeout-ms:300000}") int readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * Hàm sinh nhạc chính từ Google Colab AI
     * @param prompt Đoạn mô tả bài hát
     * @return dữ liệu âm thanh thô để lớp lưu trữ ghi ra file
     */
    public GeneratedMusic generateMusic(String prompt, boolean instrumental) {
        if (colabApiUrl == null || colabApiUrl.isEmpty()) {
            throw new RuntimeException("Chưa cấu hình link 'colab.music-api.url' trong application.properties!");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Gửi dữ liệu theo đúng cấu hình PromptRequest của Python FastAPI trên Colab
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("instrumental", instrumental);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Đang gửi yêu cầu sinh nhạc sang GPU Colab... Prompt: {}", prompt);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(colabApiUrl, entity, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] audioBytes = response.getBody();
                log.info("AI Colab đã tạo nhạc thành công! Kích thước: {} bytes", audioBytes.length);

                MediaType mediaType = response.getHeaders().getContentType();
                String contentType = mediaType != null
                        ? mediaType.toString()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE;
                return new GeneratedMusic(
                        "AI Generated - " + prompt,
                        audioBytes,
                        contentType,
                        null);
            } else {
                throw new RuntimeException("Server AI Colab trả về lỗi: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Lỗi kết nối tới Server AI Google Colab: {}", e.getMessage());
            throw new RuntimeException("Không thể kết nối tới lõi xử lý AI cá nhân trên Google Colab. Hãy chắc chắn bạn đã bật Colab.");
        }
    }
}
