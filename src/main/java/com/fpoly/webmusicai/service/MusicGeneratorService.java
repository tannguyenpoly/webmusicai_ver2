package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
@Slf4j
public class MusicGeneratorService {

    @Value("${colab.music-api.url}")
    private String colabApiUrl;

    @Value("${music.generator.engine:colab}")
    private String defaultEngine;

    @Value("${musicapi.api-key:}")
    private String musicApiKey;

    @Value("${musicapi.base-url:https://api.musicapi.ai}")
    private String musicApiBaseUrl;

    @Value("${musicapi.model-version:sonic-v5}")
    private String musicApiModelVersion;

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
     * Hàm sinh nhạc chính từ Google Colab AI hoặc MusicAPI.ai tùy theo cấu hình mặc định
     */
    public GeneratedMusic generateMusic(String prompt, boolean instrumental) {
        return generateMusic(prompt, instrumental, null);
    }

    /**
     * Hàm sinh nhạc chính hỗ trợ lựa chọn cụ thể Engine
     */
    public GeneratedMusic generateMusic(String prompt, boolean instrumental, String engine) {
        String activeEngine = (engine == null || engine.trim().isEmpty()) ? defaultEngine : engine;
        log.info("Bắt đầu sinh nhạc sử dụng Engine: {}", activeEngine);

        if ("musicapi".equalsIgnoreCase(activeEngine)) {
            return generateMusicWithMusicApi(prompt, instrumental);
        } else {
            return generateMusicWithColab(prompt, instrumental);
        }
    }

    /**
     * Sinh nhạc qua Google Colab (Model cũ)
     */
    private GeneratedMusic generateMusicWithColab(String prompt, boolean instrumental) {
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

    /**
     * Sinh nhạc qua API musicapi.ai (Suno/Udio)
     */
    private GeneratedMusic generateMusicWithMusicApi(String prompt, boolean instrumental) {
        if (musicApiKey == null || musicApiKey.trim().isEmpty()) {
            throw new RuntimeException("Chưa cấu hình API key 'musicapi.api-key' trong application.properties!");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(musicApiKey);

            // Gửi yêu cầu khởi tạo task sinh nhạc với đúng tham số gpt_description_prompt khi custom_mode = false
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("task_type", "create_music");
            requestBody.put("gpt_description_prompt", prompt);
            requestBody.put("custom_mode", false);
            requestBody.put("mv", musicApiModelVersion);
            requestBody.put("make_instrumental", instrumental);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            String createUrl = musicApiBaseUrl + "/api/v1/sonic/create";

            log.info("Đang gửi yêu cầu tạo nhạc tới musicapi.ai... Prompt: {}", prompt);
            ResponseEntity<Map> response = restTemplate.postForEntity(createUrl, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String taskId = (String) body.get("task_id");
                if (taskId == null) {
                    throw new RuntimeException("Không nhận được task_id từ phản hồi của musicapi.ai: " + body);
                }
                log.info("Đã tạo task tạo nhạc trên musicapi.ai thành công. Task ID: {}", taskId);

                String statusUrl = musicApiBaseUrl + "/api/v1/sonic/task/" + taskId;
                String audioUrl = null;
                int maxRetries = 60; 
                int retryCount = 0;

                while (retryCount < maxRetries) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Tiến trình bị gián đoạn khi đang chờ kết quả từ musicapi.ai.");
                    }

                    Thread.sleep(10000);
                    retryCount++;
                    log.info("Đang kiểm tra trạng thái Task {} (Lần {})", taskId, retryCount);

                    HttpHeaders pollHeaders = new HttpHeaders();
                    pollHeaders.setBearerAuth(musicApiKey);
                    HttpEntity<Void> pollEntity = new HttpEntity<>(pollHeaders);

                    ResponseEntity<Map> pollResponse = restTemplate.exchange(statusUrl, HttpMethod.GET, pollEntity, Map.class);

                    if (pollResponse.getStatusCode().is2xxSuccessful() && pollResponse.getBody() != null) {
                        Map<String, Object> pollBody = pollResponse.getBody();
                        List<Map<String, Object>> dataList = (List<Map<String, Object>>) pollBody.get("data");
                        if (dataList != null && !dataList.isEmpty()) {
                            Map<String, Object> firstClip = dataList.get(0);
                            String state = (String) firstClip.get("state");
                            log.info("Trạng thái Task {} từ musicapi.ai (clip 1): {}", taskId, state);

                            if ("succeeded".equalsIgnoreCase(state)) {
                                audioUrl = (String) firstClip.get("audio_url");
                                log.info("Task hoàn thành! Audio URL: {}", audioUrl);
                                break;
                            } else if ("failed".equalsIgnoreCase(state)) {
                                throw new RuntimeException("Tác vụ sinh nhạc trên musicapi.ai bị báo lỗi thất bại!");
                            }
                        } else {
                            log.info("Trạng thái Task {} từ musicapi.ai: Chưa có dữ liệu dataList", taskId);
                        }
                    }
                }

                if (audioUrl == null) {
                    throw new RuntimeException("Không tìm thấy link nhạc hoặc vượt quá thời gian sinh nhạc trên musicapi.ai!");
                }

                // Tải file âm thanh về bộ nhớ tạm thời
                log.info("Đang tải file audio từ: {}", audioUrl);
                HttpHeaders downloadHeaders = new HttpHeaders();
                HttpEntity<Void> downloadEntity = new HttpEntity<>(downloadHeaders);
                ResponseEntity<byte[]> downloadResponse = restTemplate.exchange(audioUrl, HttpMethod.GET, downloadEntity, byte[].class);

                if (downloadResponse.getStatusCode().is2xxSuccessful() && downloadResponse.getBody() != null) {
                    byte[] audioBytes = downloadResponse.getBody();
                    MediaType mediaType = downloadResponse.getHeaders().getContentType();
                    String contentType = mediaType != null ? mediaType.toString() : "audio/mpeg";

                    log.info("Tải file audio thành công! Kích thước: {} bytes", audioBytes.length);
                    return new GeneratedMusic(
                            "AI Generated - " + prompt,
                            audioBytes,
                            contentType,
                            null);
                } else {
                    throw new RuntimeException("Không thể download file nhạc từ URL: " + audioUrl);
                }
            } else {
                throw new RuntimeException("Yêu cầu tạo nhạc tới musicapi.ai không thành công. Status code: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi API musicapi.ai: {}", e.getMessage());
            throw new RuntimeException("Sinh nhạc qua musicapi.ai thất bại: " + e.getMessage(), e);
        }
    }
}
