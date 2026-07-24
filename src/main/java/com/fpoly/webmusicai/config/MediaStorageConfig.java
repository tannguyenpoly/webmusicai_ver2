package com.fpoly.webmusicai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fpoly.webmusicai.service.AudioStorageService;

@Configuration
public class MediaStorageConfig implements WebMvcConfigurer {

    private final AudioStorageService audioStorageService;

    public MediaStorageConfig(AudioStorageService audioStorageService) {
        this.audioStorageService = audioStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/audio/**")
                .addResourceLocations(audioStorageService.getResourceLocation())
                .setCachePeriod(3600);
    }
}
