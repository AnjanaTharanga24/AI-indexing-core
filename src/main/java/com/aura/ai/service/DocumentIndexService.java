package com.aura.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface DocumentIndexService {
    Map<String, Object> saveDocument(MultipartFile file, Integer templateId,
                                     String templateName, Map<String, String> extractedFields,
                                     String username, String sessionId);
}
