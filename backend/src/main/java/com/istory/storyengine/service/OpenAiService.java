package com.istory.storyengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.istory.storyengine.service.openai.OpenAiProperties;
import com.istory.storyengine.service.prompt.PromptSystem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private final WebClient openAiWebClient;
    private final OpenAiProperties props;

    private final ObjectMapper mapper = new ObjectMapper();

    public String generateJsonText(String userPromptJson) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("instructions", PromptSystem.SYSTEM_PROMPT);
        body.put("input", userPromptJson);
        body.put("temperature", 0.7);

        String raw = openAiWebClient
                .post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(b -> Mono.error(new RuntimeException(
                                        "OpenAI HTTP " + resp.statusCode().value() + " body=" + b
                                )))
                )
                .bodyToMono(String.class)
                .block();

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("OpenAI returned empty response body");
        }

        try {
            JsonNode root = mapper.readTree(raw);

            String extracted = extractOutputText(root);

            if (extracted == null || extracted.isBlank()) {
                // 🔥 on log le raw pour comprendre
                throw new RuntimeException("OpenAI output_text empty. raw=" + raw);
            }

            // Petit log de sanity
            log.debug("OpenAI extracted (first 80 chars) => {}", extracted.substring(0, Math.min(80, extracted.length())));

            return extracted.trim();

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response JSON. raw=" + raw, e);
        }
    }

    public String generateTitleJson(String contextJson) {
        // même mécanique que generateJsonText, mais avec TITLE_PROMPT
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getModel());
        body.put("instructions", PromptSystem.TITLE_PROMPT);
        body.put("input", contextJson);
        body.put("temperature", 0.7);

        String raw = openAiWebClient
                .post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(b -> Mono.error(new RuntimeException(
                                        "OpenAI HTTP " + resp.statusCode().value() + " body=" + b
                                )))
                )
                .bodyToMono(String.class)
                .block();

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("OpenAI returned empty response body");
        }

        try {
            JsonNode root = mapper.readTree(raw);
            String extracted = extractOutputText(root);
            if (extracted == null || extracted.isBlank()) {
                throw new RuntimeException("OpenAI output_text empty. raw=" + raw);
            }
            return extracted.trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response JSON. raw=" + raw, e);
        }
    }


    private String extractOutputText(JsonNode root) {
        StringBuilder sb = new StringBuilder();

        JsonNode output = root.get("output");
        if (output == null || !output.isArray()) return null;

        for (JsonNode item : output) {
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) continue;

            for (JsonNode c : content) {
                String type = c.path("type").asText("");
                if ("output_text".equals(type)) {
                    String text = c.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!sb.isEmpty()) sb.append("\n");
                        sb.append(text);
                    }
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
