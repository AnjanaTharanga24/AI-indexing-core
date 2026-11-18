package com.aura.ai.service;

import com.aura.ai.dto.FieldExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FieldExtractionService {
    FieldExtractionResponse extractFields(MultipartFile file, Integer templateId,
                                          String templateName, String username, String sessionId);
}
