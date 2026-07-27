package com.fpoly.webmusicai.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class AudioStorageService {

    private final Path storageDirectory;

    public AudioStorageService(@Value("${audio.storage.location:./uploads/audio}") String location) {
        this.storageDirectory = Paths.get(location).toAbsolutePath().normalize();
    }

    public String store(byte[] audioBytes, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Dữ liệu âm thanh rỗng");
        }

        String extension = extensionFor(contentType);
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(storageDirectory);
            Files.write(storageDirectory.resolve(filename), audioBytes,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return "/media/audio/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("Không thể lưu file âm thanh", e);
        }
    }

    public Resource load(String filename) {
        String safeFilename = Paths.get(filename).getFileName().toString();
        try {
            Resource resource = new UrlResource(storageDirectory.resolve(safeFilename).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Không tìm thấy file âm thanh");
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc file âm thanh", e);
        }
    }

    public void deleteByUrl(String audioUrl) {
        if (audioUrl == null || !audioUrl.startsWith("/media/audio/")) {
            return;
        }
        String filename = audioUrl.substring("/media/audio/".length());
        try {
            Files.deleteIfExists(storageDirectory.resolve(Paths.get(filename).getFileName()));
        } catch (IOException ignored) {
            // Database cleanup must not fail only because an old file cannot be removed.
        }
    }

    public String getResourceLocation() {
        String uri = storageDirectory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private String extensionFor(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("mpeg") || normalized.contains("mp3")) {
            return ".mp3";
        }
        if (normalized.contains("ogg")) {
            return ".ogg";
        }
        if (normalized.contains("flac")) {
            return ".flac";
        }
        return ".wav";
    }
}
