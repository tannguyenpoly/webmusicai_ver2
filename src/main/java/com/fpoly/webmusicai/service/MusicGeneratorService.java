package com.fpoly.webmusicai.service;

import org.springframework.stereotype.Service;

@Service
public class MusicGeneratorService {

    public String generateMusic(String prompt) {
        try {
            // Giả lập thời gian AI (như Suno) suy nghĩ mất 5 giây
            Thread.sleep(5000); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Trả về một link nhạc mẫu có sẵn trên mạng sau khi "suy nghĩ" xong
        return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3";
    }
}