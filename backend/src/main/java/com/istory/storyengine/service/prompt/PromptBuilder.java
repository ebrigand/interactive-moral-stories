package com.istory.storyengine.service.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PromptBuilder {

    private final ObjectMapper mapper = new ObjectMapper();

    public String build(PromptUser data) {
        Map<String, Object> root = new HashMap<>();

        root.put("storyContext", Map.of(
                "targetAge", data.targetAge(),
                "character", data.character(),
                "environment", data.environment(),
                "mission", data.mission(),
                "tone", data.tone()
        ));

        root.put("storyState", Map.of(
                "segmentIndex", data.segmentIndex(),
                "immoralChoicesCount", data.immoralChoicesCount(),
                "lastChoiceSummary", data.lastChoiceSummary(),
                "isFailureImminent", data.isFailureImminent()
        ));

        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Prompt serialization failed", e);
        }
    }
}
