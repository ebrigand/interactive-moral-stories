package com.istory.storyengine.dto;

import jakarta.validation.constraints.NotBlank;

public record TtsRequest(
        @NotBlank String text,
        String voice,          // ex: "alloy", "verse", "nova"...
        String format          // ex: "mp3" (par défaut)
) {}
