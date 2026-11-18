package com.aura.ai.controller;

import com.aura.ai.dto.FieldExtractionResponse;
import com.aura.ai.service.FieldExtractionService;
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

import java.util.Map;

@RestController
@RequestMapping("api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Field Extraction", description = "AI-powered field value extraction from documents")
public class FieldExtractionController {

    private final FieldExtractionService fieldExtractionService;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fields extracted successfully", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json"))
    })
    @Operation(summary = "Extract field values from a document",
            description = "Uploads a document and extracts field values based on the template's defined fields")
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractFields(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") Integer templateId,
            @RequestParam("templateName") String templateName) {

        String username = TokenUtil.getUsernameFromToken();
        String sessionId = TokenUtil.getSessionIdFromToken();

        log.info("Field extraction request — templateId={}, templateName={}, fileName={}, user={}",
                templateId, templateName, file.getOriginalFilename(), username);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", "File is required"
            ));
        }

        try {
            FieldExtractionResponse result = fieldExtractionService.extractFields(
                    file, templateId, templateName, username, sessionId);
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Fields extracted successfully",
                    "data", result
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to extract fields: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to extract fields: " + e.getMessage()
            ));
        }
    }
}
