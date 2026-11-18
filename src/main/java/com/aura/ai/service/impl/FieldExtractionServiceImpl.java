package com.aura.ai.service.impl;

import com.aura.ai.client.OllamaClient;
import com.aura.ai.client.V4Client;
import com.aura.ai.dto.FieldExtractionResponse;
import com.aura.ai.service.FieldExtractionService;
import com.aura.ai.util.DocumentImageConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldExtractionServiceImpl implements FieldExtractionService {

    private final OllamaClient ollamaClient;
    private final V4Client v4Client;
    private final ObjectMapper objectMapper;

    @Override
    public FieldExtractionResponse extractFields(MultipartFile file, Integer templateId,
                                                  String templateName, String username, String sessionId) {

        List<Map<String, Object>> fields = v4Client.fetchTemplateFields(templateId);
        if (fields.isEmpty()) {
            throw new IllegalStateException("No fields found for template ID: " + templateId);
        }

        byte[] imageBytes = DocumentImageConverter.convertToImage(file);
        if (imageBytes == null) {
            v4Client.saveLog(username, sessionId, templateName,
                    file.getOriginalFilename(), "Failed to process uploaded file for extraction", "FAILED");
            throw new RuntimeException("Failed to process the uploaded file");
        }

        String prompt = buildExtractionPrompt(templateName, fields);
        String aiResponse = ollamaClient.analyzeImage(imageBytes, prompt);

        if (aiResponse == null || aiResponse.isBlank()) {
            v4Client.saveLog(username, sessionId, templateName,
                    file.getOriginalFilename(), "AI model returned empty response for extraction", "FAILED");
            throw new RuntimeException("AI model returned empty response");
        }

        log.info("Field extraction AI response for template {}: {}", templateId, aiResponse);

        FieldExtractionResponse result = parseExtractionResponse(aiResponse, templateId, templateName);

        v4Client.saveLog(username, sessionId, templateName,
                file.getOriginalFilename(),
                "Field extraction: " + result.getExtractedFields().size() + " fields extracted",
                result.getStatus());

        return result;
    }

    private String buildExtractionPrompt(String templateName, List<Map<String, Object>> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a document data extraction expert. ");
        sb.append("This document is a \"").append(templateName).append("\". ");
        sb.append("Extract the values for the following fields from the document image.\n\n");
        sb.append("Fields to extract:\n\n");

        for (Map<String, Object> field : fields) {
            String displayAs = field.get("displayAs") != null ? field.get("displayAs").toString() : "Unknown";
            String type = field.get("type") != null ? field.get("type").toString() : "Text";
            sb.append("- ").append(displayAs).append(" (type: ").append(type).append(")\n");
        }

        sb.append("\nReturn ONLY a valid JSON object with these fields (no extra text):\n");
        sb.append("{\n");
        sb.append("  \"extractedFields\": {\n");

        for (int i = 0; i < fields.size(); i++) {
            String displayAs = fields.get(i).get("displayAs") != null
                    ? fields.get(i).get("displayAs").toString() : "Unknown";
            sb.append("    \"").append(displayAs).append("\": \"<extracted value or empty string if not found>\"");
            if (i < fields.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  },\n");
        sb.append("  \"confidence\": <0.0 to 1.0 how confident you are in the overall extraction>\n");
        sb.append("}\n\n");
        sb.append("If a field value is not visible or not applicable, set it to an empty string.");

        return sb.toString();
    }

    private FieldExtractionResponse parseExtractionResponse(String aiResponse, Integer templateId, String templateName) {
        try {
            String jsonStr = extractJson(aiResponse);
            JsonNode json = objectMapper.readTree(jsonStr);

            Map<String, String> extractedFields = new LinkedHashMap<>();
            if (json.has("extractedFields") && json.get("extractedFields").isObject()) {
                JsonNode fieldsNode = json.get("extractedFields");
                Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    extractedFields.put(entry.getKey(), entry.getValue().asText());
                }
            }

            double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.0;
            String status = confidence >= 0.7 ? "EXTRACTED" : "NEEDS_REVIEW";

            return FieldExtractionResponse.builder()
                    .templateId(templateId)
                    .templateName(templateName)
                    .extractedFields(extractedFields)
                    .confidence(confidence)
                    .model(ollamaClient.getModel())
                    .status(status)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse extraction response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse extraction response: " + e.getMessage());
        }
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
                    if (candidate.contains("extractedFields") || candidate.contains("confidence")) {
                        return candidate;
                    }
                    braceStart = -1;
                }
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
