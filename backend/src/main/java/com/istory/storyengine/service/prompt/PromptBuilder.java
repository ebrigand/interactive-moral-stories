package com.istory.storyengine.service.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptBuilder {

    private final ObjectMapper mapper = new ObjectMapper();

    public String build(PromptUser data) {
        try {
            Map<String, Object> storyContext = new LinkedHashMap<>();
            storyContext.put("targetAge", data.targetAge());
            storyContext.put("playerName", data.playerName());
            storyContext.put("theme", data.theme());
            storyContext.put("plannedSegments", data.plannedSegments());

            storyContext.put("character", data.character());
            storyContext.put("environment", data.environment());
            storyContext.put("mission", data.mission());
            storyContext.put("tone", data.tone());
            storyContext.put("title", data.title());

            // 🔥 Variété
            storyContext.put("storySeed", data.storySeed());
            storyContext.put("openingStyle", data.openingStyle());
            storyContext.put("variationPack", data.variationPack());
            storyContext.put("avoidList", data.avoidList() == null ? List.of() : data.avoidList());

            Map<String, Object> storyState = new LinkedHashMap<>();
            storyState.put("segmentIndex", data.segmentIndex());
            storyState.put("immoralChoicesCount", data.immoralChoicesCount());
            storyState.put("lastChoiceSummary", data.lastChoiceSummary());
            storyState.put("isFailureImminent", data.isFailureImminent());

            Map<String, Object> root = Map.of(
                    "storyContext", storyContext,
                    "storyState", storyState
            );

            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Prompt serialization failed", e);
        }
    }
}
