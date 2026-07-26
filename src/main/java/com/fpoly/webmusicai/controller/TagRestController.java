package com.fpoly.webmusicai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Tag;
import com.fpoly.webmusicai.repository.TagRepository;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/tags")
public class TagRestController {

    @Autowired
    private TagRepository tagRepo;

    @GetMapping
    public ResponseEntity<?> getAllTags() {
        List<Tag> tags = tagRepo.findAll();
        return ResponseEntity.ok(tags);
    }
}
