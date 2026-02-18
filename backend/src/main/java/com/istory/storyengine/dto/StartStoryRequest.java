package com.istory.storyengine.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StartStoryRequest(
        @Min(3) @Max(16) int targetAge,

        @NotBlank String playerName,
        @NotBlank String theme,

        // ✅ champ canonique attendu par le front
        @Min(4) @Max(60)
        @JsonProperty("chapterCount")
        @JsonAlias({"durationMinutes"}) // tolère encore les vieux clients
        int chapterCount,

        // optionnels
        String character,
        String environment,
        String mission,
        String tone
) {}
