package com.istory.storyengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class StorySegment {

    private String narration;

    // ✅ Dialogues structurés pour TTS multi-voix
    private List<Utterance> utterances = new ArrayList<>();

    private List<Choice> choices = new ArrayList<>();

    private boolean ended;

    private String explanation;

    private Set<String> moralChoiceIds = new HashSet<>();
}
