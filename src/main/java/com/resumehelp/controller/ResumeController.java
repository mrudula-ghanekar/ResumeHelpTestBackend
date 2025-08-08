package com.resumehelp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumehelp.service.OpenAIService;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ResumeController {
    @Autowired private OpenAIService openAIService;
    private final Tika tika = new Tika();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/analyze-file")
    public ResponseEntity<Object> analyzeFile(
            @RequestParam("mode") String mode,
            @RequestParam("role") String role,
            @RequestParam(value="file", required=false) MultipartFile file,
            @RequestParam(value="files", required=false) List<MultipartFile> files,
            @RequestParam(value="jd_file", required=false) MultipartFile jdFile
    ) {
        try {
            if ("company".equalsIgnoreCase(mode)) {
                // Company: need JD + multiple resumes
                if (files == null || files.isEmpty() || jdFile == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Missing JD or resumes."));
                }
                List<String> resumeTexts = new ArrayList<>();
                List<String> fileNames = new ArrayList<>();
                for (MultipartFile f : files) {
                    resumeTexts.add(tika.parseToString(f.getInputStream()));
                    fileNames.add(f.getOriginalFilename());
                }
                String jdText = tika.parseToString(jdFile.getInputStream());
                String res = openAIService.compareResumesInBatchWithJD(resumeTexts, fileNames, jdText, "no-email@example.com");
                return parseJson(res);
            } else {
                // Candidate: single resume
                if (file == null) return ResponseEntity.badRequest().body(Map.of("error", "Missing resume file."));
                String text = tika.parseToString(file.getInputStream());
                String res = openAIService.analyzeResume(text, role, mode);
                return parseJson(res);
            }
        } catch (IOException | TikaException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<Object> parseJson(String jsonString) {
        try {
            Object obj = mapper.readValue(jsonString, Object.class);
            return ResponseEntity.ok(obj);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Invalid JSON from AI: " + ex.getMessage()));
        }
    }
}
