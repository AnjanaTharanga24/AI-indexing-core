package com.aura.ai.client;

import com.aura.ai.dto.TemplateProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class V4Client {

    @Value("${v4.api.base-url}")
    private String v4BaseUrl;

    private final RestTemplate restTemplate;

    public V4Client() {
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> updateTemplateProfile(TemplateProfileResponse profile, String model) {
        try {
            String url = v4BaseUrl + "/ai/templates/profile/update";

            Map<String, Object> request = new HashMap<>();
            request.put("templateId", profile.getTemplateId());
            request.put("summary", profile.getSummary());
            request.put("identifyingFeatures", profile.getIdentifyingFeatures());
            request.put("alternativeNames", profile.getAlternativeNames());
            request.put("distinguishingFeatures", profile.getDistinguishingFeatures());
            request.put("model", model);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, buildHeaders());

            log.info("Updating template profile in V4 for templateId={}", profile.getTemplateId());

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to update template profile in V4: {}", e.getMessage(), e);
            return Map.of("status", 500, "message", "Failed to update V4: " + e.getMessage());
        }
    }

    public void saveLog(String username, String sessionId, String templateName,
                        String fileName, String description, String status) {
        saveLog(username, sessionId, templateName, fileName, description, status, null);
    }

    public void saveLog(String username, String sessionId, String templateName,
                        String fileName, String description, String status, Long fileId) {
        try {
            String url = v4BaseUrl + "/dashboard/log-access/" + sessionId;

            Map<String, Object> request = new HashMap<>();
            request.put("operation", fileId != null ? "INDEX" : "AI_PROFILE");
            request.put("templateName", templateName);
            request.put("docName", fileName);
            request.put("description", description + " [" + status + "]");
            if (fileId != null) {
                request.put("fileId", fileId);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, buildHeaders());

            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Log saved in V4 for template={}, status={}, fileId={}", templateName, status, fileId);
        } catch (Exception e) {
            log.error("Failed to save log in V4: {}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> fetchAllTemplates() {
        try {
            String url = v4BaseUrl + "/template/list";

            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                List<?> rawList = (List<?>) body.get("data");
                List<Map<String, Object>> result = new java.util.ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            Map<String, Object> template = new HashMap<>();
                            template.put("id", Integer.valueOf(entry.getKey().toString()));
                            template.put("name", entry.getValue().toString());
                            result.add(template);
                        }
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch templates from V4: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public List<Map<String, Object>> fetchTemplateFields(int templateId) {
        try {
            String url = v4BaseUrl + "/admin/template/edit/" + templateId;

            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data.containsKey("fields") && data.get("fields") != null) {
                    return (List<Map<String, Object>>) data.get("fields");
                }
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch template fields from V4 for templateId={}: {}", templateId, e.getMessage(), e);
            return List.of();
        }
    }

    public List<Map<String, Object>> fetchAllProfiles() {
        try {
            String url = v4BaseUrl + "/ai/templates/profiles/all";

            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("data")) {
                return (List<Map<String, Object>>) body.get("data");
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch profiles from V4: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public Map<String, Object> saveDocumentIndex(org.springframework.web.multipart.MultipartFile file,
                                                    String metadataJson, String username, String sessionId) {
        String url = v4BaseUrl + "/image/index-with-document";

        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("document", fileResource);
            body.add("metadata", metadataJson);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            String token = extractTokenFromRequest();
            if (token != null) {
                headers.setBearerAuth(token);
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("Saving document index to V4: url={}, file={}, user={}", url, file.getOriginalFilename(), username);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to save document index in V4: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to save document to V4: " + e.getMessage());
        }
    }

    public Map<String, Object> sendToPending(org.springframework.web.multipart.MultipartFile file, String folderName) {
        String url = v4BaseUrl + "/archive/store-document-named";

        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("document", fileResource);
            body.add("folderName", folderName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            String token = extractTokenFromRequest();
            if (token != null) {
                headers.setBearerAuth(token);
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            log.info("Sending document to V4 pending: file={}, folder={}", file.getOriginalFilename(), folderName);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to send document to V4 pending: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send to pending: " + e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String token = extractTokenFromRequest();
        if (token != null) {
            headers.setBearerAuth(token);
        }

        return headers;
    }

    private String extractTokenFromRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract JWT from request: {}", e.getMessage());
        }
        return null;
    }
}
