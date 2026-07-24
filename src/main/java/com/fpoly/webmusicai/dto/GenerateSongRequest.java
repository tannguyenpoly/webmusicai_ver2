package com.fpoly.webmusicai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateSongRequest {

    @NotBlank(message = "Mô tả bài hát không được để trống")
    @Size(max = 1000, message = "Mô tả bài hát không được vượt quá 1000 ký tự")
    private String prompt;

    @Size(max = 255, message = "Tên bài hát không được vượt quá 255 ký tự")
    private String title;

    private boolean instrumental = true;

    private Integer genreId;
}
