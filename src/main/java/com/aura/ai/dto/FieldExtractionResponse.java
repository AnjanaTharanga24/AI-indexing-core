package com.aura.ai.dto;

import lombok.*;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FieldExtractionResponse {
    private Integer templateId;
    private String templateName;
    private Map<String, String> extractedFields;
    private double confidence;
    private String model;
    private String status;
}
