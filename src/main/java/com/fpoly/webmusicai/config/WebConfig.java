package com.fpoly.webmusicai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${audio.storage.location:./uploads/audio}")
    private String audioStorageLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose the /uploads/audio/** URL path to serve files from the local file system
        // The path needs to be resolved to an absolute path with "file:" prefix.
        String resolvedPath = "file:" + audioStorageLocation.replace("./", System.getProperty("user.dir").replace("\\", "/") + "/");
        registry.addResourceHandler("/uploads/audio/**").addResourceLocations(resolvedPath);
    }
}