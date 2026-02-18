package com.istory.storyengine.validation;

import com.istory.storyengine.model.Choice;
import com.istory.storyengine.model.StorySegment;
import com.istory.storyengine.model.Utterance;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StorySegmentValidator {

    // Match "...." (guillemets doubles)
    private static final Pattern QUOTED_DIALOGUE = Pattern.compile("\"(.*?)\"", Pattern.DOTALL);

    public void validate(StorySegment segment) {

        if (segment.getNarration() == null || segment.getNarration().isBlank()) {
            throw new IllegalStateException(
                    segment.isEnded()
                            ? "Ended segment must include a final narration"
                            : "Narration is empty"
            );
        }

        // Normalise utterances -> liste vide si null
        if (segment.getUtterances() == null) {
            segment.setUtterances(Collections.emptyList());
        }

        // ✅ Validation quotes ↔ utterances (pour voix différentes)
        validateUtterancesMatchQuotes(segment);

        // -------------------------
        // SEGMENT DE FIN
        // -------------------------
        if (segment.isEnded()) {

            if (segment.getChoices() == null || !segment.getChoices().isEmpty()) {
                throw new IllegalStateException("Ended segment must have empty choices");
            }

            if (segment.getMoralChoiceIds() == null || !segment.getMoralChoiceIds().isEmpty()) {
                throw new IllegalStateException("Ended segment must have empty moralChoiceIds");
            }

            if (segment.getExplanation() == null || segment.getExplanation().isBlank()) {
                throw new IllegalStateException("Ended segment must include explanation");
            }

            return; // ✅ fin
        }

        // -------------------------
        // SEGMENT NORMAL
        // -------------------------
        if (segment.getChoices() == null || segment.getChoices().size() != 4) {
            throw new IllegalStateException("Segment must have exactly 4 choices");
        }

        if (segment.getMoralChoiceIds() == null || segment.getMoralChoiceIds().size() != 2) {
            throw new IllegalStateException("Segment must have exactly 2 moral choices");
        }

        // Vérifie textes des choix + ids
        for (Choice c : segment.getChoices()) {
            if (c.getId() == null || c.getId().isBlank()) {
                throw new IllegalStateException("Choice id missing");
            }
            if (c.getText() == null || c.getText().isBlank()) {
                throw new IllegalStateException("Choice text missing for id=" + c.getId());
            }
        }

        // IDs uniques
        Set<String> choiceIds = segment.getChoices().stream()
                .map(Choice::getId)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        if (choiceIds.size() != 4) {
            throw new IllegalStateException("Choice ids must be unique");
        }

        // Vérifie que les 2 moraux font partie des choix
        for (String moralId : segment.getMoralChoiceIds()) {
            if (moralId == null || moralId.isBlank()) {
                throw new IllegalStateException("Moral choice id is blank");
            }
            if (!choiceIds.contains(moralId.trim().toUpperCase())) {
                throw new IllegalStateException("Invalid moralChoiceId: " + moralId);
            }
        }

        // explanation optionnelle en segment normal
        if (segment.getExplanation() == null) {
            segment.setExplanation("");
        }
    }

    private void validateUtterancesMatchQuotes(StorySegment segment) {
        final String narration = segment.getNarration();
        final List<Utterance> utterances = segment.getUtterances() == null ? List.of() : segment.getUtterances();

        final int quotedCount = countQuotedDialogues(narration);

        // Si narration contient des dialogues "..." => utterances doit matcher exactement
        if (quotedCount > 0) {
            if (utterances.isEmpty()) {
                throw new IllegalStateException(
                        "Narration contains " + quotedCount + " quoted dialogues but utterances is empty"
                );
            }
            if (utterances.size() != quotedCount) {
                throw new IllegalStateException(
                        "Narration contains " + quotedCount + " quoted dialogues but utterances has " + utterances.size()
                );
            }
        }

        // Validation des utterances si présentes
        for (int i = 0; i < utterances.size(); i++) {
            Utterance u = utterances.get(i);
            if (u == null) {
                throw new IllegalStateException("Utterance[" + i + "] is null");
            }
            if (u.getSpeaker() == null || u.getSpeaker().isBlank()) {
                throw new IllegalStateException("Utterance[" + i + "] speaker missing");
            }
            if (u.getAgeGroup() == null || u.getAgeGroup().isBlank()) {
                throw new IllegalStateException("Utterance[" + i + "] ageGroup missing (CHILD/ADULT)");
            }
            if (u.getText() == null || u.getText().isBlank()) {
                throw new IllegalStateException("Utterance[" + i + "] text missing");
            }
        }
    }

    private int countQuotedDialogues(String narration) {
        Matcher m = QUOTED_DIALOGUE.matcher(narration);
        int count = 0;
        while (m.find()) {
            String inside = m.group(1);
            if (inside != null && !inside.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
