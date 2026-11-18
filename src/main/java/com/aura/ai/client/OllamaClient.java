package com.aura.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
public class OllamaClient {

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    private final ObjectMapper objectMapper;
    private final Semaphore modelLock = new Semaphore(1);

    public OllamaClient() {
        this.objectMapper = new ObjectMapper();
    }

    public String analyzeImage(byte[] imageBytes, String prompt) {
        try {
            modelLock.acquire();
            log.info("Acquired model lock for request");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for model lock");
            return null;
        }
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> request = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "images", List.of(base64Image),
                    "stream", true,
                    "format", "json",
                    "options", Map.of("num_predict", 4096, "temperature", 0.0, "seed", 42)
            );

            String jsonBody = objectMapper.writeValueAsString(request);
            String endpoint = baseUrl + "/api/generate";

            log.info("Sending streaming request to Ollama at {} with model {}", endpoint, model);

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "AuraDocs-AI");
            conn.setRequestProperty("ngrok-skip-browser-warning", "true");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(1_200_000);
            conn.setDoOutput(true);

            conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            int status = conn.getResponseCode();
            if (status != 200) {
                java.io.InputStream errorStream = conn.getErrorStream();
                String errorBody = errorStream != null
                        ? new String(errorStream.readAllBytes(), StandardCharsets.UTF_8)
                        : "(no error body, status=" + status + ")";
                log.error("Ollama returned status {}: {}", status, errorBody);
                return null;
            }

            StringBuilder fullResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    JsonNode node = objectMapper.readTree(line);
                    if (node.has("response")) {
                        fullResponse.append(node.get("response").asText());
                    }
                    if (node.has("done") && node.get("done").asBoolean()) {
                        log.info("Ollama streaming complete");
                        break;
                    }
                }
            }

            conn.disconnect();

            String result = fullResponse.toString().trim();
            if (result.isEmpty()) {
                log.error("Ollama returned empty streaming response");
                return null;
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to call Ollama API: {}", e.getMessage(), e);
            return null;
        } finally {
            modelLock.release();
            log.info("Released model lock");
        }
    }

    public String getModel() {
        return model;
    }
}
