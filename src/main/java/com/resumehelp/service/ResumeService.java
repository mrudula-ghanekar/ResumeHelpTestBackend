package com.resumehelp.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ResumeService {

    public String extractText(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("❌ Resume file is missing.");
        }

        String fileName = file.getOriginalFilename().toLowerCase();

        try (InputStream inputStream = file.getInputStream()) {
            if (fileName.endsWith(".pdf")) {
                return extractPdfText(inputStream);
            } else if (fileName.endsWith(".docx")) {
                return extractDocxText(inputStream);
            } else {
                throw new IllegalArgumentException("❌ Unsupported file type. Please upload a PDF or DOCX file.");
            }
        }
    }

    private String extractPdfText(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocxText(InputStream inputStream) throws Exception {
        try (XWPFDocument docx = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            docx.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return new String(sb.toString().getBytes(), StandardCharsets.UTF_8);
        }
    }
}
