package com.fpoly.webmusicai.service.music;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import com.fpoly.webmusicai.service.GeneratedMusic;

/** Adapter cho ACE-Step API chạy trên Colab/worker. */
@Component
public class AceStepMusicProvider extends AbstractHttpMusicProvider implements MusicGenerationProvider {

    @Value("${ace-step.api.url:}")
    private String apiUrl;

    public AceStepMusicProvider(
            @Value("${music.providers.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${music.providers.read-timeout-ms:420000}") int readTimeout,
            @Value("${music.providers.health-timeout-ms:3000}") int healthTimeout) {
        super(connectTimeout, readTimeout, healthTimeout);
    }

    @Override public String code() { return "ace-step"; }
    @Override public String displayName() { return "ACE-Step (Colab)"; }
    @Override public boolean supportsVocalLanguage() { return true; }

    @Override
    public boolean isAvailable() {
        if (apiUrl == null || apiUrl.isBlank()) return false;
        try {
            return healthRestTemplate.exchange(apiUrl.replaceAll("/$", "") + "/health", HttpMethod.GET, null, Map.class)
                    .getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException error) {
            return false;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public GeneratedMusic generate(GenerationSpec spec) {
        if (!isAvailable()) {
            throw new IllegalStateException("ACE-Step chưa trực tuyến. Hãy chạy worker Colab có API và cập nhật ACE_STEP_API_URL.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caption", spec.prompt());
        body.put("instrumental", spec.instrumental());
        body.put("lyrics", spec.hasLyrics() ? spec.lyrics() : null);
        body.put("vocal_language", "Tiếng Việt".equalsIgnoreCase(spec.vocalLanguage()) ? "vi" : "en");
        body.put("duration", spec.durationSeconds());

        ResponseEntity<byte[]> response = restTemplate.postForEntity(
                apiUrl.replaceAll("/$", "") + "/generate", jsonRequest(body, null), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("ACE-Step không trả về file âm thanh.");
        }
        String contentType = response.getHeaders().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : response.getHeaders().getContentType().toString();
        return new GeneratedMusic("ACE-Step - " + spec.prompt(), response.getBody(), contentType,
                spec.hasLyrics() ? spec.lyrics() : null, null, "COMPLETED");
    }
}
