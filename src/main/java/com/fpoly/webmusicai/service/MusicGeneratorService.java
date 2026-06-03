package com.fpoly.webmusicai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MusicGeneratorService {

	@Value("${musicapi.key}")
	private String apiKey;

	private final RestTemplate restTemplate = new RestTemplate();
	private final String BASE_URL = "https://api.musicapi.ai";

	public String generateMusic(String prompt, boolean instrumental) {
		String taskId = createJob(prompt, instrumental); // truyền 2 tham số
		return waitForAudioUrl(taskId);
	}


private String createJob(String prompt, boolean instrumental) {  // nhận 2 tham số
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + apiKey);
    headers.setContentType(MediaType.APPLICATION_JSON);

    String body = String.format("""
        {
          "custom_mode": false,
          "mv": "sonic-v4",
          "gpt_description_prompt": "%s",
          "make_instrumental": %b
        }
        """, prompt.replace("\"", "'"), instrumental);  // dùng %b cho boolean

    ResponseEntity<Map> response = restTemplate.postForEntity(
        BASE_URL + "/api/v1/sonic/create",
        new HttpEntity<>(body, headers),
        Map.class
    );

    Map responseBody = response.getBody();
    if (responseBody == null || responseBody.get("task_id") == null) {
        throw new RuntimeException("Không tạo được job: " + responseBody);
    }

    String taskId = (String) responseBody.get("task_id");
	log.info("Tạo job thành công, task_id: {}", taskId);
	return taskId;
}

	private String waitForAudioUrl(String taskId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + apiKey);
		HttpEntity<Void> entity = new HttpEntity<>(headers);

		for (int i = 0; i < 36; i++) {
			try {
				Thread.sleep(5000);

				ResponseEntity<Map> response = restTemplate.exchange(BASE_URL + "/api/v1/sonic/task/" + taskId,
						HttpMethod.GET, entity, Map.class);

				Map result = response.getBody();
				if (result == null || "not_ready".equals(result.get("type")))
					continue;

				List<Map> dataList = (List<Map>) result.get("data");
				if (dataList == null)
					continue;

				for (Map clip : dataList) {
					if ("succeeded".equals(clip.get("state"))) {
						String audioUrl = (String) clip.get("audio_url");
						log.info("Gen nhạc xong! URL: {}", audioUrl);
						return audioUrl;
					}
				}

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Bị ngắt");
			}
		}
		throw new RuntimeException("Timeout: gen nhạc quá 3 phút");
	}
}