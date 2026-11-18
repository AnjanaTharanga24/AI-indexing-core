package com.aura.ai.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ClassificationResponse {
    private Integer matchedTemplateId;
    private String matchedTemplateName;
    private double confidence;
    private String reason;
    private String status;
    private String model;
    private List<CandidateMatch> candidates;

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class CandidateMatch {
        private Integer templateId;
        private String templateName;
        private double confidence;
    }
}
