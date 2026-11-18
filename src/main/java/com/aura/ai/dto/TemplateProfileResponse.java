package com.aura.ai.dto;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder(toBuilder = true)
public class TemplateProfileResponse {
    private Integer templateId;
    private String templateName;
    private String summary;
    private List<String> identifyingFeatures;
    private List<String> alternativeNames;
    private List<String> distinguishingFeatures;
    private String status;
    private Integer version;
    private String model;
}
