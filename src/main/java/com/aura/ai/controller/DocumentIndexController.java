package com.aura.ai.controller;

import com.aura.ai.service.DocumentIndexService;
import com.aura.ai.util.TokenUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@Tag(name = "AI Document Index", description = "Save AI-extracted document data to V4")
public class DocumentIndexController {

    private final DocumentIndexService documentIndexService;
    private final com.aura.ai.client.V4Client v4Client;
    private final ObjectMapper objectMapper;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document saved successfully", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = "application/json"))
    })
    @Operation(summary = "Save extracted document to V4",
            description = "Saves a document with AI-extracted field values to the V4 archive")
    @PostMapping(value = "/save-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> saveDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") Integer templateId,
            @RequestParam("templateName") String templateName,
            @RequestParam("extractedFields") String extractedFieldsJson) {

        String username = TokenUtil.getUsernameFromToken();
        String sessionId = TokenUtil.getSessionIdFromToken();

        log.info("Save document request — templateId={}, templateName={}, fileName={}, user={}",
                templateId, templateName, file.getOriginalFilename(), username);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", "File is required"
            ));
        }

        try {
            Map<String, String> extractedFields = objectMapper.readValue(
                    extractedFieldsJson, new TypeReference<Map<String, String>>() {});

            Map<String, Object> result = documentIndexService.saveDocument(
                    file, templateId, templateName, extractedFields, username, sessionId);

            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Document saved successfully",
                    "data", result != null ? result : Map.of()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to save document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to save document: " + e.getMessage()
            ));
        }
    }

    @Operation(summary = "Send document to V4 pending folder",
            description = "Uploads a document to the V4 pending area for later indexing")
    @PostMapping(value = "/send-to-pending", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> sendToPending(
            @RequestParam("file") MultipartFile file) {

        String username = TokenUtil.getUsernameFromToken();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "message", "File is required"
            ));
        }

        try {
            String folderName = "AI_Review";
            log.info("Send to pending request — fileName={}, folder={}, user={}",
                    file.getOriginalFilename(), folderName, username);
            Map<String, Object> result = v4Client.sendToPending(file, folderName);
            return ResponseEntity.ok(Map.of(
                    "status", HttpStatus.OK.value(),
                    "message", "Document sent to pending",
                    "data", result != null ? result : Map.of()
            ));
        } catch (Exception e) {
            log.error("Failed to send to pending: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "message", "Failed to send to pending: " + e.getMessage()
            ));
        }
    }
}
