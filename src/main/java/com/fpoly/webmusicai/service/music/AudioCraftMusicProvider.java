package com.fpoly.webmusicai.service.music;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import com.fpoly.webmusicai.service.GeneratedMusic;

import lombok.extern.slf4j.Slf4j;

/** Adapter cho FastAPI AudioCraft đang chạy ở Google Colab. */
@Component
@Slf4j
public class AudioCraftMusicProvider extends AbstractHttpMusicProvider implements MusicGenerationProvider {

    @Value("${audiocraft.api.url:${colab.music-api.url:}}")
    private String apiUrl;

    public AudioCraftMusicProvider(
            @Value("${music.providers.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${music.providers.read-timeout-ms:300000}") int readTimeout,
            @Value("${music.providers.health-timeout-ms:3000}") int healthTimeout) {
        super(connectTimeout, readTimeout, healthTimeout);
    }

    @Override public String code() { return "audiocraft"; }
    @Override public String displayName() { return "AudioCraft (Colab)"; }
    @Override public boolean supportsLyrics() { return false; }

    @Override
    public boolean isAvailable() {
        if (apiUrl == null || apiUrl.isBlank()) return false;
        try {
            ResponseEntity<Void> response = healthRestTemplate.exchange(apiUrl, HttpMethod.OPTIONS, null, Void.class);
            return !response.getStatusCode().is5xxServerError();
        } catch (HttpStatusCodeException error) {
            HttpStatusCode status = error.getStatusCode();
            return status.value() == 401 || status.value() == 403 || status.value() == 405;
        } catch (ResourceAccessException error) {
            log.info("AudioCraft Colab chưa sẵn sàng: {}", error.getMessage());
            return false;
        }
    }

    @Override
    public GeneratedMusic generate(GenerationSpec spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("AudioCraft Colab chưa trực tuyến. Hãy chạy notebook và cập nhật AUDIOCRAFT_API_URL.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", spec.prompt());
        body.put("instrumental", true);
        if (spec.durationSeconds() != null) body.put("duration_seconds", spec.durationSeconds());
        ResponseEntity<byte[]> response = restTemplate.postForEntity(apiUrl, jsonRequest(body, null), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("AudioCraft không trả về file âm thanh.");
        }
        String contentType = response.getHeaders().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : response.getHeaders().getContentType().toString();
        return new GeneratedMusic("AudioCraft - " + spec.prompt(), response.getBody(), contentType, null, null, "COMPLETED");
    }
}
