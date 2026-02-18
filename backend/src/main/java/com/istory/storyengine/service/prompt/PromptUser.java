package com.istory.storyengine.service.prompt;

import java.util.List;

public record PromptUser(
        int targetAge,
        String playerName,
        String theme,

        // ✅ remplace durationMinutes
        int chapterCount,

        int plannedSegments,

        String character,
        String environment,
        String mission,
        String tone,
        String title,

        // 🔥 Variété
        String storySeed,
        String openingStyle,
        String variationPack,
        List<String> avoidList,

        int segmentIndex,
        int immoralChoicesCount,
        String lastChoiceSummary,
        boolean isFailureImminent
) {}
