package com.fpoly.webmusicai.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Calls the separate genre-analysis worker; it does not persist uploaded audio. */
@Service
public class MusicAnalysisService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${colab.genre-api.url:}")
    private String genreApiUrl;

    public boolean isConfigured() {
        return genreApiUrl != null && !genreApiUrl.isBlank();
    }

    public AnalysisResult analyze(MultipartFile file) {
        if (!isConfigured()) {
            throw new IllegalStateException("Chưa cấu hình máy phân tích thể loại. Hãy đặt COLAB_GENRE_API_URL trong application-local.properties.");
        }
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "reference-audio" : file.getOriginalFilename();
                }
            };
            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(MediaType.parseMediaType(
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType()));
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(resource, partHeaders));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    genreApiUrl, new HttpEntity<>(body, headers), Map.class);
            Map<?, ?> payload = response.getBody();
            if (payload == null) throw new IllegalStateException("Máy phân tích không trả dữ liệu.");
            Object predictions = payload.get("predictions");
            if (!(predictions instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) {
                throw new IllegalStateException("Kết quả phân tích không đúng định dạng.");
            }
            String label = String.valueOf(first.get("label"));
            Object scoreValue = first.get("score");
            double score = scoreValue instanceof Number number ? number.doubleValue() : 0d;
            if (label.isBlank() || "null".equalsIgnoreCase(label)) throw new IllegalStateException("Không nhận diện được thể loại.");
            return new AnalysisResult(label, score);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Không thể kết nối máy phân tích thể loại: " + error.getMessage());
        }
    }

    public record AnalysisResult(String label, double confidence) {}
}
