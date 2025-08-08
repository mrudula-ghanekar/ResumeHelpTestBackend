package com.resumehelp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public String analyzeResume(String resumeText, String role, String mode) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an honest and intelligent AI career advisor and resume evaluator.\n");
        prompt.append("Strictly analyze the resume below ONLY for the role: '").append(role).append("'.\n\n");

        prompt.append("### TASK:\n")
              .append("1. Compare required skills vs resume.\n")
              .append("2. Return \"suited_for_role\": \"Yes\" or \"No\"\n")
              .append("3. Extract candidate name or fallback to 'Unnamed Candidate'\n")
              .append("4. Include strong_points (include 6+ years exp even if unrelated)\n")
              .append("5. weak_points must describe real gaps or general ones\n");

        if ("candidate".equalsIgnoreCase(mode)) {
            prompt.append("6. Always provide:\n")
                  .append("   - online_courses\n")
                  .append("   - youtube_channels\n")
                  .append("   - career_guides\n")
                  .append("   - alternative_roles\n")
                  .append("   - skills_to_learn\n");
        } else {
            prompt.append("6. Provide comparison_score and suggestions\n");
        }

        prompt.append("\nOnly return this JSON (never leave fields blank):\n{\n")
              .append("  \"status\": \"success\",\n")
              .append("  \"candidate_name\": \"...\",\n")
              .append("  \"suited_for_role\": \"Yes\" or \"No\",\n")
              .append("  \"strong_points\": [\"...\"],\n")
              .append("  \"weak_points\": [\"...\"],\n");

        if ("candidate".equalsIgnoreCase(mode)) {
            prompt.append("  \"improvement_suggestions\": [\"...\"],\n")
                  .append("  \"recommendations\": {\n")
                  .append("    \"online_courses\": [\"...\"],\n")
                  .append("    \"youtube_channels\": [\"...\"],\n")
                  .append("    \"career_guides\": [\"...\"],\n")
                  .append("    \"alternative_roles\": [\"...\"],\n")
                  .append("    \"skills_to_learn\": [\"...\"]\n")
                  .append("  }\n");
        } else {
            prompt.append("  \"comparison_score\": \"Ranks higher than XX%\",\n")
                  .append("  \"improvement_suggestions\": [\"...\"]\n");
        }

        prompt.append("}\n\n### Resume:\n").append(resumeText);

        String rawJson = callOpenAI(prompt.toString());
        return enforceFallbacks(rawJson, mode);
    }

    public String generateImprovedResume(String resumeText, String role) {
        String prompt = "You are an AI resume optimizer. Improve this resume for the role: '" + role + "'.\n"
                + "- Use bullet points and relevant keywords.\n"
                + "- Make it clear and ATS-friendly.\n"
                + "Return:\n{ \"status\": \"success\", \"improved_resume\": \"...\" }\n\n"
                + "### Resume:\n" + resumeText;

        return callOpenAI(prompt);
    }

    public String compareResumesInBatch(List<String> resumeTexts, List<String> fileNames, String role) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < resumeTexts.size(); i++) {
            combined.append("Resume ").append(i + 1)
                    .append(" (File: ").append(fileNames.get(i)).append("):\n")
                    .append(resumeTexts.get(i)).append("\n\n");
        }

        String prompt = "You are an AI recruiter evaluating candidates for the role: '" + role + "'.\n"
                + "- Extract name or fallback to file name.\n"
                + "- Score (0–100) based on match.\n"
                + "Output JSON array:\n"
                + "[ { \"index\": 0, \"file_name\": \"...\", \"candidate_name\": \"...\", \"score\": 87, \"summary\": \"...\" } ]\n\n"
                + "### Resumes:\n" + combined;

        return callOpenAI(prompt);
    }

    public String compareResumesInBatchWithJD(List<String> resumeTexts, List<String> fileNames, String jobDescription, String userEmail) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < resumeTexts.size(); i++) {
            combined.append("Resume ").append(i + 1)
                    .append(" (File: ").append(fileNames.get(i)).append("):\n")
                    .append(resumeTexts.get(i)).append("\n\n");
        }

       String prompt = "You are an AI recruiter comparing resumes to the following Job Description:\n\n"
        + jobDescription + "\n\n"
        + "- Carefully review each resume for relevance and match to the JD.\n"
        + "- Score each resume from 0 to 100 based on how well it fits the JD.\n"
        + "- Extract candidate name from resume, fallback to 'Unnamed' if missing.\n"
        + "- Extract current or most recent company name, fallback to 'N/A' if missing.\n"
        + "- Output a JSON object with:\n"
        + "    1. \"ranked_resumes\": a list of resumes sorted by score (highest first), each with:\n"
        + "       - file_name\n"
        + "       - candidate_name\n"
        + "       - company { name }\n"
        + "       - score\n"
        + "       - rank (1 for best match, 2 for second best, etc.)\n"
        + "       - summary (brief overview)\n"
        + "       - rank_summary (1-line reason why this resume fits the JD)\n"
        + "    2. \"top_fits\": list of resume labels (e.g., \"Resume 1\", \"Resume 2\") where score ≥ 80.\n\n"
        + "Return **only** a valid JSON object with the structure above — no explanation or extra text.\n\n"
        + "### Resumes:\n"
        + combined;

        return callOpenAI(prompt);
    }

    private String callOpenAI(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4");
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(OPENAI_API_URL, HttpMethod.POST, request, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");

            if (choices == null || choices.isEmpty()) return "{\"error\":\"Empty OpenAI response\"}";

            String aiResponse = String.valueOf(((Map) choices.get(0).get("message")).get("content")).trim();
            return extractJson(aiResponse);
        } catch (Exception e) {
            return "{\"error\":\"API Error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String extractJson(String aiResponse) {
        int objStart = aiResponse.indexOf('{');
        int objEnd = aiResponse.lastIndexOf('}');
        if (objStart != -1 && objEnd != -1) {
            return aiResponse.substring(objStart, objEnd + 1).trim();
        }
        return "{\"error\":\"Failed to extract JSON from response\"}";
    }

    private String enforceFallbacks(String rawJson, String mode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = (ObjectNode) mapper.readTree(rawJson);

            if (!root.hasNonNull("strong_points") || root.get("strong_points").isEmpty()) {
                root.putArray("strong_points").add("Possesses significant experience or background worth building upon");
            }

            if (!root.hasNonNull("weak_points") || root.get("weak_points").isEmpty()) {
                root.putArray("weak_points").add("Lacks some role-specific tools or certifications");
            }

            if ("candidate".equalsIgnoreCase(mode) && root.has("recommendations")) {
                ObjectNode recs = (ObjectNode) root.get("recommendations");

                enforceArrayDefault(recs, "online_courses", "Try 'Career Essentials in Tech' on Coursera or edX");
                enforceArrayDefault(recs, "youtube_channels", "Search 'Tech With Tim' or 'Simplilearn'");
                enforceArrayDefault(recs, "career_guides", "See careerfoundry.com or indeed.com/career-advice");
                enforceArrayDefault(recs, "alternative_roles", "Consider roles like QA Analyst, Support Engineer");
                enforceArrayDefault(recs, "skills_to_learn", "Communication, project documentation, basic SQL");
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return rawJson;
        }
    }

    private void enforceArrayDefault(ObjectNode node, String key, String fallbackValue) {
        if (!node.hasNonNull(key) || node.get(key).isEmpty()) {
            node.putArray(key).add(fallbackValue);
        }
    }
}