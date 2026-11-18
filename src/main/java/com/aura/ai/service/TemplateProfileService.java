package com.aura.ai.service;

import com.aura.ai.dto.TemplateProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface TemplateProfileService {
    TemplateProfileResponse generateProfile(MultipartFile file, Integer templateId,
                                            String templateName, String username, String sessionId);
}
