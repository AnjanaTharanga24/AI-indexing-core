package com.aura.ai.controller;

import com.aura.ai.dto.ClassificationResponse;
import com.aura.ai.service.ClassificationService;
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
@RequestMapping("api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Document Classification", description = "AI-powered document classification")
public class ClassificationController {

    private final ClassificationService classificationService;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document classified successfully", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json"))
    })
    @Operation(summary = "Classify a document",
            description = "Uploads a document and AI identifies which template it belongs to based on saved profiles")
    @PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> classify(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "templateIds", required = false) List<Integer> templateIds) {

        String username = TokenUtil.getUsernameFromToken();
        String sessionId = TokenUtil.getSessionIdFromToken();

        log.info("Classification request — fileName={}, user={}, templateIds={}", file.getOriginalFilename(), username, templateIds);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", "File is required"
            ));
        }

        try {
            ClassificationResponse result = classificationService.classify(file, username, sessionId, templateIds);
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", result.getConfidence() >= 0.75
                            ? "Document classified successfully"
                            : "Document classified with low confidence - manual review recommended",
                    "data", result
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to classify document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to classify document: " + e.getMessage()
            ));
        }
    }
}
