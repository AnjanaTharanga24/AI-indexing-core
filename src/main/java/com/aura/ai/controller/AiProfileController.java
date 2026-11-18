package com.aura.ai.controller;

import com.aura.ai.dto.TemplateProfileResponse;
import com.aura.ai.service.TemplateProfileService;
import com.aura.ai.util.TokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/ai/templates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Template Profile", description = "AI-powered template profile generation")
public class AiProfileController {

    private final TemplateProfileService templateProfileService;
    private final com.aura.ai.client.V4Client v4Client;

    @Operation(summary = "Get all templates from V4",
            description = "Fetches the list of all templates from V4 for dropdown selection")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllTemplates() {
        log.info("Fetching all templates from V4");
        try {
            List<Map<String, Object>> templates = v4Client.fetchAllTemplates();
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Templates fetched successfully",
                    "data", templates
            ));
        } catch (Exception e) {
            log.error("Failed to fetch templates: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to fetch templates"
            ));
        }
    }

    @Operation(summary = "Get all template profiles from V4",
            description = "Fetches all templates that have AI profiles generated")
    @GetMapping("/profiles")
    public ResponseEntity<Map<String, Object>> getAllProfiles() {
        log.info("Fetching all template profiles from V4");
        try {
            List<Map<String, Object>> profiles = v4Client.fetchAllProfiles();
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Profiles fetched successfully",
                    "data", profiles
            ));
        } catch (Exception e) {
            log.error("Failed to fetch profiles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to fetch profiles"
            ));
        }
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile generated successfully", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json"))
    })
    @Operation(summary = "Generate AI profile for a template",
            description = "Uploads a sample document and uses AI to generate a profile. Results are saved to V4 template table and logged.")
    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> generateProfile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") Integer templateId,
            @RequestParam("templateName") String templateName) {

        String username = TokenUtil.getUsernameFromToken();
        String sessionId = TokenUtil.getSessionIdFromToken();

        log.info("Generate AI profile request — templateId={}, templateName={}, fileName={}, user={}",
                templateId, templateName, file.getOriginalFilename(), username);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", "File is required"
            ));
        }

        try {
            TemplateProfileResponse profile = templateProfileService.generateProfile(
                    file, templateId, templateName, username, sessionId);
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Profile generated successfully",
                    "data", profile
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to generate profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to generate profile: " + e.getMessage()
            ));
        }
    }
}
