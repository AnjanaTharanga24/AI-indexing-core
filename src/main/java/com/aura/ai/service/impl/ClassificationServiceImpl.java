package com.aura.ai.service.impl;

import com.aura.ai.client.OllamaClient;
import com.aura.ai.client.V4Client;
import com.aura.ai.dto.ClassificationResponse;
import com.aura.ai.service.ClassificationService;
import com.aura.ai.util.DocumentImageConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationServiceImpl implements ClassificationService {

    private final OllamaClient ollamaClient;
    private final V4Client v4Client;
    private final ObjectMapper objectMapper;

    @Override
    public ClassificationResponse classify(MultipartFile file, String username, String sessionId) {
        return classify(file, username, sessionId, null);
    }

    @Override
    public ClassificationResponse classify(MultipartFile file, String username, String sessionId, List<Integer> templateIds) {

        List<Map<String, Object>> profiles = v4Client.fetchAllProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No template profiles found. Please generate profiles first.");
        }

        if (templateIds != null && !templateIds.isEmpty()) {
            profiles = profiles.stream()
                    .filter(p -> templateIds.contains(((Number) p.get("templateId")).intValue()))
                    .toList();
            if (profiles.isEmpty()) {
                throw new IllegalStateException("None of the selected templates have profiles.");
            }
        }

        byte[] imageBytes = DocumentImageConverter.convertToImage(file);
        if (imageBytes == null) {
            throw new RuntimeException("Failed to process the uploaded file");
        }

        String prompt = buildClassificationPrompt(profiles);
        String aiResponse = ollamaClient.analyzeImage(imageBytes, prompt);

        if (aiResponse == null || aiResponse.isBlank()) {
            v4Client.saveLog(username, sessionId, "", file.getOriginalFilename(),
                    "Classification failed - empty AI response", "FAILED");
            throw new RuntimeException("AI model returned empty response");
        }

        log.info("Classification AI response: {}", aiResponse);

        ClassificationResponse result = parseClassificationResponse(aiResponse);

        if (!"NO_MATCH".equals(result.getStatus())) {
            String status = result.getConfidence() >= 0.70 ? "CLASSIFIED" : "NEEDS_REVIEW";
            result.setStatus(status);
        }
        result.setModel(ollamaClient.getModel());

        v4Client.saveLog(username, sessionId,
                result.getMatchedTemplateName() != null ? result.getMatchedTemplateName() : "None",
                file.getOriginalFilename(),
                "Classification: " + (result.getMatchedTemplateName() != null ? result.getMatchedTemplateName() : "No Match")
                        + " (confidence: " + String.format("%.0f%%", result.getConfidence() * 100) + ")",
                result.getStatus());

        return result;
    }

    private String buildClassificationPrompt(List<Map<String, Object>> profiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict document classification expert. ");
        sb.append("Analyze this document image and determine which template it belongs to.\n\n");
        sb.append("CRITICAL CLASSIFICATION RULES:\n");
        sb.append("- IGNORE generic/common fields like name, address, date, phone, email, signature, ID number when matching.\n");
        sb.append("  These fields exist on almost every document and are NOT useful for classification.\n");
        sb.append("- Match ONLY based on UNIQUE identifying features: document title/header, specific section headings, logos, layout structure, unique labels.\n");
        sb.append("- Two documents that both have 'name' and 'address' are NOT the same type — look at what makes them DIFFERENT.\n\n");
        sb.append("CONFIDENCE SCORING RULES:\n");
        sb.append("- Only give confidence above 0.8 if the document has the template's UNIQUE identifying features (specific headers, logos, layout)\n");
        sb.append("- Give confidence 0.5-0.8 if the document shares some unique features but you are not fully certain\n");
        sb.append("- Give confidence below 0.3 if the document does NOT match any template (e.g. screenshots, random images, unrelated documents)\n");
        sb.append("- Do NOT force a high confidence match. If the document is not a real match, confidence MUST be low\n");
        sb.append("- A document that is a screenshot, dashboard, webpage, or generic image is NOT a match for any document template\n\n");
        sb.append("Available templates:\n\n");

        for (Map<String, Object> profile : profiles) {
            sb.append("Template ID: ").append(profile.get("templateId")).append("\n");
            sb.append("Template Name: ").append(profile.get("templateName")).append("\n");
            sb.append("Summary: ").append(profile.get("summary")).append("\n");
            sb.append("Identifying Features: ").append(profile.get("identifyingFeatures")).append("\n");
            sb.append("Alternative Names: ").append(profile.get("alternativeNames")).append("\n");
            sb.append("Distinguishing Features: ").append(profile.get("distinguishingFeatures")).append("\n\n");
        }

        sb.append("Return ONLY a valid JSON object with these fields (no extra text):\n");
        sb.append("{\n");
        sb.append("  \"matchedTemplateId\": <the template ID that best matches, or null if no match>,\n");
        sb.append("  \"matchedTemplateName\": \"<the template name, or 'NO_MATCH' if nothing fits>\",\n");
        sb.append("  \"confidence\": <0.0 to 1.0 — be strict and honest, do not inflate>,\n");
        sb.append("  \"reason\": \"<brief explanation of why this template matches or why nothing matches>\",\n");
        sb.append("  \"candidates\": [\n");
        sb.append("    { \"templateId\": <id>, \"templateName\": \"<name>\", \"confidence\": <0.0-1.0> }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("IMPORTANT: The 'candidates' array must list ALL templates ranked by confidence from highest to lowest. Every template must appear with its honest confidence score.\n");
        sb.append("If the document does not clearly match any template, set matchedTemplateId to null, matchedTemplateName to 'NO_MATCH', and the top confidence below 0.3.");

        return sb.toString();
    }

    private ClassificationResponse parseClassificationResponse(String aiResponse) {
        try {
            String jsonStr = extractJson(aiResponse);
            if (jsonStr == null) {
                log.warn("No JSON found in classification response, returning NO_MATCH");
                return ClassificationResponse.builder()
                        .matchedTemplateId(null)
                        .matchedTemplateName("No Match")
                        .confidence(0.0)
                        .reason("AI could not identify this document as any known template: " + truncate(aiResponse, 200))
                        .status("NO_MATCH")
                        .build();
            }

            JsonNode json = objectMapper.readTree(jsonStr);

            Integer templateId = null;
            if (json.has("matchedTemplateId") && !json.get("matchedTemplateId").isNull()) {
                templateId = json.get("matchedTemplateId").asInt();
            }

            String templateName = "No Match";
            if (json.has("matchedTemplateName") && !json.get("matchedTemplateName").isNull()) {
                String name = json.get("matchedTemplateName").asText();
                if (!"NO_MATCH".equalsIgnoreCase(name)) {
                    templateName = name;
                }
            }

            double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.0;
            String reason = json.has("reason") ? json.get("reason").asText() : "";

            List<ClassificationResponse.CandidateMatch> candidates = new java.util.ArrayList<>();
            if (json.has("candidates") && json.get("candidates").isArray()) {
                for (JsonNode c : json.get("candidates")) {
                    Integer cId = (c.has("templateId") && !c.get("templateId").isNull()) ? c.get("templateId").asInt() : null;
                    String cName = c.has("templateName") ? c.get("templateName").asText() : "Unknown";
                    double cConf = c.has("confidence") ? c.get("confidence").asDouble() : 0.0;
                    candidates.add(ClassificationResponse.CandidateMatch.builder()
                            .templateId(cId).templateName(cName).confidence(cConf).build());
                }
            }

            if (templateId == null || "No Match".equals(templateName)) {
                return ClassificationResponse.builder()
                        .matchedTemplateId(null)
                        .matchedTemplateName("No Match")
                        .confidence(confidence)
                        .reason(reason)
                        .status("NO_MATCH")
                        .candidates(candidates)
                        .build();
            }

            return ClassificationResponse.builder()
                    .matchedTemplateId(templateId)
                    .matchedTemplateName(templateName)
                    .confidence(confidence)
                    .reason(reason)
                    .candidates(candidates)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse classification response: {}", e.getMessage(), e);
            return ClassificationResponse.builder()
                    .matchedTemplateId(null)
                    .matchedTemplateName("No Match")
                    .confidence(0.0)
                    .reason("Failed to parse AI response: " + truncate(aiResponse, 200))
                    .status("NO_MATCH")
                    .build();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String extractJson(String text) {
        int braceStart = -1;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) braceStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && braceStart >= 0) {
                    String candidate = text.substring(braceStart, i + 1);
                    if (candidate.contains("matchedTemplateId") || candidate.contains("confidence")) {
                        return fixMalformedJson(candidate);
                    }
                    braceStart = -1;
                }
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return fixMalformedJson(text.substring(start, end + 1));
        }
        return null;
    }

    private String fixMalformedJson(String json) {
        json = json.replaceAll("\"\\s*\\n\\s*\"", "\",\n\"");
        json = json.replaceAll("\\]\\s*\\n\\s*\"", "],\n\"");
        json = json.replaceAll("\"\\s+\"(matched|confidence|reason|status)", "\", \"$1");
        json = json.replaceAll("(\\d)\\s+\"", "$1, \"");
        return json;
    }
}
