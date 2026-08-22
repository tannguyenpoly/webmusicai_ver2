package com.fpoly.webmusicai.service.music;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fpoly.webmusicai.service.GeneratedMusic;

/** MusicAPI.ai Sonic adapter. API key chỉ đọc từ biến môi trường. */
@Component
public class MusicApiMusicProvider extends AbstractHttpMusicProvider implements MusicGenerationProvider {

    @Value("${musicapi.base-url:https://api.musicapi.ai}")
    private String baseUrl;
    @Value("${musicapi.api-key:}")
    private String apiKey;
    @Value("${musicapi.model-version:sonic-v4-5}")
    private String modelVersion;

    public MusicApiMusicProvider(
            @Value("${music.providers.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${music.providers.read-timeout-ms:420000}") int readTimeout,
            @Value("${music.providers.health-timeout-ms:3000}") int healthTimeout) {
        super(connectTimeout, readTimeout, healthTimeout);
    }

    @Override public String code() { return "musicapi"; }
    @Override public String displayName() { return "MusicAPI.ai"; }
    @Override public boolean supportsVocalGender() { return true; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    @SuppressWarnings("unchecked")
    public GeneratedMusic generate(GenerationSpec spec) {
        if (!isAvailable()) throw new IllegalStateException("MusicAPI.ai chưa có MUSICAPI_API_KEY.");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task_type", "create_music");
        request.put("gpt_description_prompt", spec.prompt());
        request.put("custom_mode", spec.hasLyrics());
        request.put("make_instrumental", spec.instrumental());
        request.put("mv", modelVersion);
        if (spec.hasLyrics()) request.put("lyrics", spec.lyrics());
        if (!spec.instrumental() && spec.hasVocalGenderSelection()) {
            request.put("vocal_gender", spec.providerGenderCode());
        }

        ResponseEntity<Map> submitted = restTemplate.postForEntity(
                baseUrl + "/api/v1/sonic/create", jsonRequest(request, apiKey), Map.class);
        Map<String, Object> submittedBody = submitted.getBody();
        String taskId = submittedBody == null ? null : String.valueOf(submittedBody.get("task_id"));
        if (taskId == null || taskId.isBlank() || "null".equals(taskId)) {
            throw new IllegalStateException("MusicAPI.ai không trả về task_id.");
        }

        for (int attempt = 0; attempt < 40; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Đã dừng tác vụ tạo nhạc.");
            try { Thread.sleep(15000L); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Đã dừng tác vụ tạo nhạc.");
            }
            ResponseEntity<Map> result = restTemplate.exchange(baseUrl + "/api/v1/sonic/task/" + taskId,
                    HttpMethod.GET, jsonRequest(Map.of(), apiKey), Map.class);
            Map<String, Object> payload = result.getBody();
            Object data = payload == null ? null : payload.get("data");
            if (!(data instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> clip)) continue;
            String state = String.valueOf(clip.get("state"));
            if ("failed".equalsIgnoreCase(state)) throw new IllegalStateException("MusicAPI.ai báo tác vụ thất bại.");
            if (!"succeeded".equalsIgnoreCase(state)) continue;
            Object urlValue = clip.get("audio_url");
            if (urlValue == null) throw new IllegalStateException("MusicAPI.ai không có audio_url.");
            ResponseEntity<byte[]> audio = download(String.valueOf(urlValue));
            String contentType = audio.getHeaders().getContentType() == null ? "audio/mpeg" : audio.getHeaders().getContentType().toString();
            Object title = clip.get("title");
            Object lyrics = clip.get("lyrics");
            return new GeneratedMusic(title == null ? "MusicAPI.ai" : String.valueOf(title), audio.getBody(), contentType,
                    lyrics == null ? spec.lyrics() : String.valueOf(lyrics), taskId, "succeeded");
        }
        throw new IllegalStateException("MusicAPI.ai quá thời gian chờ xử lý.");
    }
}
