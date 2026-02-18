package com.istory.storyengine.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChoiceRequest(
        @NotBlank
        @JsonProperty("choice")
        @JsonAlias({"choiceId"})
        String choice
) {}
