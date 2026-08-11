package com.fpoly.webmusicai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class MusicAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(MusicAnalysisService.class);

    @Value("${colab.genre-api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public MusicAnalysisService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofMinutes(3)) // Tăng thời gian chờ lên 3 phút
                .build();
    }

    public String analyzeGenre(Path audioFilePath) {
        logger.info("Bắt đầu phân tích thể loại cho file: {}", audioFilePath);
        try {
            return analyzeAudioBytes(Files.readAllBytes(audioFilePath), audioFilePath.getFileName().toString());
        } catch (IOException e) {
            logger.error("Lỗi khi đọc file audio: {}", audioFilePath, e);
            return null;
        }
    }

    public String analyzeGenre(MultipartFile audioFile) throws IOException {
        logger.info("Bắt đầu phân tích thể loại cho file tải lên: {}", audioFile.getOriginalFilename());
        return analyzeAudioBytes(audioFile.getBytes(), audioFile.getOriginalFilename());
    }

    private String analyzeAudioBytes(byte[] audioBytes, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // The FastAPI endpoint expects a file part named "file".
            // We wrap the byte array in a ByteArrayResource to send it as a file.
            ByteArrayResource contentsAsResource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            body.add("file", contentsAsResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseGenreFromResponse(response.getBody());
            } else {
                logger.error("Lỗi từ API Colab: {} - {}", response.getStatusCode(), response.getBody());
                return null;
            }
         } catch (HttpClientErrorException e) {
            logger.error("Lỗi client khi gọi API Colab: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (ResourceAccessException e) {
            logger.error("Lỗi kết nối mạng khi gọi API Colab. Kiểm tra kết nối Internet, DNS hoặc URL của ngrok.", e);
            return null;
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi phân tích thể loại", e);
            return null;
        }
    }

    private String parseGenreFromResponse(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            // Sửa lại để đọc cấu trúc JSON từ pipeline: {"predictions": [{"label": "...", "score": ...}]}
            if (rootNode.has("predictions")) {
                JsonNode predictionsNode = rootNode.get("predictions");
                if (predictionsNode.isArray() && !predictionsNode.isEmpty()) {
                    // Lấy kết quả đầu tiên (có điểm số cao nhất)
                    JsonNode topResult = predictionsNode.get(0);
                    if (topResult.has("label")) {
                        String genre = topResult.get("label").asText();
                        double score = topResult.has("score") ? topResult.get("score").asDouble() : 0.0;
                        logger.info("Thể loại được nhận dạng từ Colab: {} (Điểm: {})", genre, score);
                        return genre;
                    }
                }
            }
            logger.warn("Response từ Colab không có định dạng dự đoán hợp lệ: {}", jsonResponse);
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Lỗi khi phân tích JSON response từ API Colab: {}", jsonResponse, e);
            return null;
        }
    }
}