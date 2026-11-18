package com.aura.ai.service.impl;

import com.aura.ai.client.OllamaClient;
import com.aura.ai.client.V4Client;
import com.aura.ai.dto.TemplateProfileResponse;
import com.aura.ai.service.TemplateProfileService;
import com.aura.ai.util.DocumentImageConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateProfileServiceImpl implements TemplateProfileService {

    private final OllamaClient ollamaClient;
    private final V4Client v4Client;
    private final ObjectMapper objectMapper;

    private static final String PROFILE_PROMPT = """
            You are a document analysis expert. Analyze this document image and provide a structured profile.
            The document belongs to a template type called "%s".

            CRITICAL RULES:
            - IGNORE generic fields that appear on many document types: name, address, date, phone, email, signature, NIC/ID number fields.
              These fields exist on almost every form and are USELESS for distinguishing one document from another.
            - Focus ONLY on features that are UNIQUE to this specific document type:
              * The exact document title/header text (e.g. "Application for Driving License", "Birth Certificate")
              * Unique section headings or labels specific to this document
              * Document-specific reference number formats or codes
              * Logos, emblems, or watermarks (describe them)
              * Physical layout and structure (portrait/landscape, number of columns, table structures)
              * Unique visual elements (colored bands, borders, official seals)

            Return ONLY a valid JSON object with these fields (no extra text):
            {
              "summary": "A brief description of what this document type is for, excluding any filled-in sample values",
              "identifyingFeatures": ["list of UNIQUE titles, headers, logos, layout patterns and document-specific labels that ONLY this document type has — do NOT include generic fields like name, address, date, phone, email"],
              "alternativeNames": ["list of other common names for this document type"],
              "distinguishingFeatures": ["list of specific features that make this document DIFFERENT from other similar documents — explain what this document has that others do NOT have"]
            }
            """;

    private static final String ARRAYS_PROMPT = """
            This is a "%s" document. Look at the image and list:
            1. identifyingFeatures: the document title, unique headings, unique labels (not name/address/date)
            2. alternativeNames: other names for this document type
            3. distinguishingFeatures: what makes this document unique vs other documents
            JSON: {"identifyingFeatures":[],"alternativeNames":[],"distinguishingFeatures":[]}
            """;

    @Override
    public TemplateProfileResponse generateProfile(MultipartFile file, Integer templateId,
                                                    String templateName, String username, String sessionId) {

        byte[] imageBytes = DocumentImageConverter.convertToImage(file);
        if (imageBytes == null) {
            v4Client.saveLog(username, sessionId, templateName,
                    file.getOriginalFilename(), "Failed to process uploaded file", "FAILED");
            throw new RuntimeException("Failed to process the uploaded file");
        }

        String prompt = String.format(PROFILE_PROMPT, templateName);
        String aiResponse = ollamaClient.analyzeImage(imageBytes, prompt);

        if (aiResponse == null || aiResponse.isBlank()) {
            v4Client.saveLog(username, sessionId, templateName,
                    file.getOriginalFilename(), "AI model returned empty response", "FAILED");
            throw new RuntimeException("AI model returned empty response");
        }

        log.info("AI response for template {}: {}", templateId, aiResponse);

        TemplateProfileResponse profile = parseResponse(aiResponse, templateId, templateName);

        if (profile.getIdentifyingFeatures().isEmpty()
                || profile.getAlternativeNames().isEmpty()
                || profile.getDistinguishingFeatures().isEmpty()) {

            log.warn("Incomplete profile — arrays missing, trying short arrays-only prompt...");
            String arraysPrompt = String.format(ARRAYS_PROMPT, templateName);
            String arraysResponse = ollamaClient.analyzeImage(imageBytes, arraysPrompt);

            if (arraysResponse != null && !arraysResponse.isBlank()) {
                log.info("Arrays response: {}", arraysResponse);
                TemplateProfileResponse arraysProfile = parseResponse(arraysResponse, templateId, templateName);

                if (profile.getIdentifyingFeatures().isEmpty() && !arraysProfile.getIdentifyingFeatures().isEmpty()) {
                    profile = profile.toBuilder().identifyingFeatures(arraysProfile.getIdentifyingFeatures()).build();
                }
                if (profile.getAlternativeNames().isEmpty() && !arraysProfile.getAlternativeNames().isEmpty()) {
                    profile = profile.toBuilder().alternativeNames(arraysProfile.getAlternativeNames()).build();
                }
                if (profile.getDistinguishingFeatures().isEmpty() && !arraysProfile.getDistinguishingFeatures().isEmpty()) {
                    profile = profile.toBuilder().distinguishingFeatures(arraysProfile.getDistinguishingFeatures()).build();
                }
            }
        }

        Map<String, Object> v4Response = v4Client.updateTemplateProfile(profile, ollamaClient.getModel());
        log.info("V4 update response: {}", v4Response);

        v4Client.saveLog(username, sessionId, templateName,
                file.getOriginalFilename(),
                "AI profile generated - version " + profile.getVersion(),
                "SUCCESS");

        return profile;
    }

    private TemplateProfileResponse parseResponse(String aiResponse, Integer templateId, String templateName) {
        try {
            String jsonStr = extractJson(aiResponse);
            JsonNode json = objectMapper.readTree(jsonStr);

            String summary = json.has("summary") ? json.get("summary").asText() : "";
            List<String> identifyingFeatures = parseJsonArray(json, "identifyingFeatures");
            List<String> alternativeNames = parseJsonArray(json, "alternativeNames");
            List<String> distinguishingFeatures = parseJsonArray(json, "distinguishingFeatures");

            return TemplateProfileResponse.builder()
                    .templateId(templateId)
                    .templateName(templateName)
                    .summary(summary)
                    .identifyingFeatures(identifyingFeatures)
                    .alternativeNames(alternativeNames)
                    .distinguishingFeatures(distinguishingFeatures)
                    .status("DRAFT")
                    .version(1)
                    .model(ollamaClient.getModel())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        // Try parsing as-is first
        if (tryParseJson(text)) return text;

        // Find the outermost JSON object
        int depth = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String candidate = text.substring(start, i + 1);
                    String fixed = fixMalformedJson(candidate);
                    if (tryParseJson(fixed)) return fixed;
                }
            }
        }

        // Fallback: simple extraction
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s >= 0 && e > s) {
            return fixMalformedJson(text.substring(s, e + 1));
        }
        return text;
    }

    private boolean tryParseJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String fixMalformedJson(String json) {
        // Remove trailing commas before } or ]
        json = json.replaceAll(",\\s*([}\\]])", "$1");
        // Fix unquoted field names: {summary: -> {"summary":
        json = json.replaceAll("(?m)([{,])\\s*(\\w+)\\s*:", "$1 \"$2\":");
        // Fix missing commas between fields
        json = json.replaceAll("\"\\s*\\n\\s*\"", "\",\n\"");
        json = json.replaceAll("\\]\\s*\\n\\s*\"", "],\n\"");
        json = json.replaceAll("\"\\s+\"(identifyingFeatures|alternativeNames|distinguishingFeatures|summary)", "\", \"$1");
        json = json.replaceAll("\\]\\s+\"", "], \"");
        // Fix single quotes to double quotes
        json = json.replaceAll("'([^']*)'", "\"$1\"");
        return json;
    }

    private List<String> parseJsonArray(JsonNode json, String field) {
        if (!json.has(field) || !json.get(field).isArray()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.convertValue(json.get(field), new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
