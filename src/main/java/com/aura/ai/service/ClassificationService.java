package com.aura.ai.service;

import com.aura.ai.dto.ClassificationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ClassificationService {
    ClassificationResponse classify(MultipartFile file, String username, String sessionId);
    ClassificationResponse classify(MultipartFile file, String username, String sessionId, List<Integer> templateIds);
}
