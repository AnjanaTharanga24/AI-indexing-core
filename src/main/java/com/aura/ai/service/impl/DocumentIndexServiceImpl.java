package com.aura.ai.service.impl;

import com.aura.ai.client.V4Client;
import com.aura.ai.service.DocumentIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIndexServiceImpl implements DocumentIndexService {

    private final V4Client v4Client;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> saveDocument(MultipartFile file, Integer templateId,
                                            String templateName, Map<String, String> extractedFields,
                                            String username, String sessionId) {

        List<Map<String, Object>> templateFields = v4Client.fetchTemplateFields(templateId);
        if (templateFields.isEmpty()) {
            throw new IllegalStateException("No fields found for template ID: " + templateId);
        }

        Map<String, Integer> displayNameToFieldId = new HashMap<>();
        for (Map<String, Object> field : templateFields) {
            String displayAs = field.get("displayAs") != null ? field.get("displayAs").toString() : null;
            Object nameObj = field.get("name");
            if (displayAs != null && nameObj != null) {
                displayNameToFieldId.put(displayAs.trim().toLowerCase(), Integer.parseInt(nameObj.toString()));
            }
        }

        List<Map<String, String>> indexValueList = new ArrayList<>();
        for (Map.Entry<String, String> entry : extractedFields.entrySet()) {
            String displayName = entry.getKey().trim().toLowerCase();
            String value = entry.getValue();
            Integer fieldId = displayNameToFieldId.get(displayName);
            if (fieldId != null && value != null && !value.isBlank()) {
                Map<String, String> fieldMap = new HashMap<>();
                fieldMap.put(String.valueOf(fieldId), value);
                indexValueList.add(fieldMap);
            }
        }

        try {
            String indexValueJson = objectMapper.writeValueAsString(indexValueList);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("indexValue", indexValueJson);
            metadata.put("fileName", file.getOriginalFilename());
            metadata.put("templateId", templateId);
            metadata.put("longitude", "");
            metadata.put("latitude", "");
            metadata.put("extractedText", "");
            metadata.put("accessLevel", "P");
            metadata.put("filepath", "");
            metadata.put("folderPath", "0");

            String metadataJson = objectMapper.writeValueAsString(metadata);

            log.info("Saving document index — template={}, fields={}, file={}, user={}, metadata={}",
                    templateId, indexValueList.size(), file.getOriginalFilename(), username, metadataJson);

            Map<String, Object> result = v4Client.saveDocumentIndex(file, metadataJson, username, sessionId);

            return result;

        } catch (Exception e) {
            log.error("Failed to save document index: {}", e.getMessage(), e);
            v4Client.saveLog(username, sessionId, templateName,
                    file.getOriginalFilename(),
                    "AI document index failed: " + e.getMessage(),
                    "FAILED");
            throw new RuntimeException("Failed to save document: " + e.getMessage());
        }
    }
}
