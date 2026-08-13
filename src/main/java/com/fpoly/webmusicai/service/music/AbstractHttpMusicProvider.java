package com.fpoly.webmusicai.service.music;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

abstract class AbstractHttpMusicProvider {

    protected final RestTemplate restTemplate;
    protected final RestTemplate healthRestTemplate;

    protected AbstractHttpMusicProvider(int connectTimeout, int readTimeout, int healthTimeout) {
        this.restTemplate = new RestTemplate(requestFactory(connectTimeout, readTimeout));
        this.healthRestTemplate = new RestTemplate(requestFactory(healthTimeout, healthTimeout));
    }

    protected HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken.trim());
        }
        return new HttpEntity<>(body, headers);
    }

    protected ResponseEntity<byte[]> download(String url) {
        return restTemplate.getForEntity(url, byte[].class);
    }

    private SimpleClientHttpRequestFactory requestFactory(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
