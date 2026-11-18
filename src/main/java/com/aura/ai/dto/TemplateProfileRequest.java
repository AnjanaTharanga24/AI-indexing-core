package com.aura.ai.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TemplateProfileRequest {
    private Integer templateId;
    private String templateName;
}
