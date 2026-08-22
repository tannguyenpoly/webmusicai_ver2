package com.fpoly.webmusicai.service.music;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fpoly.webmusicai.service.GeneratedMusic;

/** Adapter Suno API chính thức. Callback sẽ được bổ sung ở giai đoạn webhook; hiện dùng polling. */
@Component
public class SunoMusicProvider extends AbstractHttpMusicProvider implements MusicGenerationProvider {

    @Value("${suno.api.url:https://api.sunoapi.org}")
    private String baseUrl;
    @Value("${suno.api-key:}")
    private String apiKey;
    @Value("${suno.model:V4_5ALL}")
    private String model;

    public SunoMusicProvider(
            @Value("${music.providers.connect-timeout-ms:10000}") int connectTimeout,
            @Value("${music.providers.read-timeout-ms:420000}") int readTimeout,
            @Value("${music.providers.health-timeout-ms:3000}") int healthTimeout) {
        super(connectTimeout, readTimeout, healthTimeout);
    }

    @Override public String code() { return "suno"; }
    @Override public String displayName() { return "Suno API"; }
    @Override public boolean supportsVocalGender() { return true; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    @SuppressWarnings("unchecked")
    public GeneratedMusic generate(GenerationSpec spec) {
        if (!isAvailable()) throw new IllegalStateException("Suno chưa có SUNO_API_KEY.");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prompt", spec.prompt());
        request.put("customMode", spec.hasLyrics());
        request.put("instrumental", spec.instrumental());
        request.put("model", model);
        if (spec.hasLyrics()) request.put("lyrics", spec.lyrics());
        if (!spec.instrumental() && spec.hasVocalGenderSelection()) {
            request.put("vocalGender", spec.providerGenderCode());
        }
        ResponseEntity<Map> submitted = restTemplate.postForEntity(
                baseUrl + "/api/v1/generate", jsonRequest(request, apiKey), Map.class);
        Map<String, Object> root = submitted.getBody();
        Object data = root == null ? null : root.get("data");
        String taskId = data instanceof Map<?, ?> dataMap && dataMap.get("taskId") != null
                ? String.valueOf(dataMap.get("taskId")) : null;
        if (taskId == null || taskId.isBlank()) throw new IllegalStateException("Suno không trả về taskId.");

        for (int attempt = 0; attempt < 40; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Đã dừng tác vụ tạo nhạc.");
            try { Thread.sleep(15000L); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Đã dừng tác vụ tạo nhạc.");
            }
            ResponseEntity<Map> result = restTemplate.exchange(
                    baseUrl + "/api/v1/generate/record-info?taskId=" + taskId,
                    HttpMethod.GET, jsonRequest(Map.of(), apiKey), Map.class);
            Map<String, Object> resultRoot = result.getBody();
            Object resultData = resultRoot == null ? null : resultRoot.get("data");
            if (!(resultData instanceof Map<?, ?> statusMap)) continue;
            String status = String.valueOf(statusMap.get("status"));
            if ("FAILED".equalsIgnoreCase(status)) throw new IllegalStateException("Suno báo tác vụ thất bại.");
            if (!"SUCCESS".equalsIgnoreCase(status)) continue;
            Object response = statusMap.get("response");
            Object songs = response instanceof Map<?, ?> responseMap ? responseMap.get("data") : null;
            if (!(songs instanceof List<?> songList) || songList.isEmpty() || !(songList.get(0) instanceof Map<?, ?> song)) {
                throw new IllegalStateException("Suno hoàn thành nhưng không trả bài nhạc.");
            }
            Object audioUrl = song.get("audioUrl");
            if (audioUrl == null) audioUrl = song.get("audio_url");
            if (audioUrl == null) throw new IllegalStateException("Suno không có audio URL.");
            ResponseEntity<byte[]> audio = download(String.valueOf(audioUrl));
            String contentType = audio.getHeaders().getContentType() == null ? "audio/mpeg" : audio.getHeaders().getContentType().toString();
            Object lyrics = song.get("lyrics");
            Object title = song.get("title");
            return new GeneratedMusic(title == null ? "Suno" : String.valueOf(title), audio.getBody(), contentType,
                    lyrics == null ? spec.lyrics() : String.valueOf(lyrics), taskId, "SUCCESS");
        }
        throw new IllegalStateException("Suno quá thời gian chờ xử lý.");
    }
}
