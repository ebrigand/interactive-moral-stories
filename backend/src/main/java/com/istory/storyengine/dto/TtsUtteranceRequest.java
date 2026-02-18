package com.istory.storyengine.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Ajout de speed (vitesse de lecture):
 * - 1.0 = normal
 * - 1.2 = +20% (ton besoin)
 * - borne côté service à [0.25, 4.0]
 */
public record TtsUtteranceRequest(
        @NotBlank String speaker,
        @NotBlank String ageGroup,
        String gender,
        @NotBlank String text,
        Double speed
) {}
