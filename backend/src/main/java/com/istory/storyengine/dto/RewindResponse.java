package com.istory.storyengine.dto;

import com.istory.storyengine.model.Choice;

import java.util.List;
import java.util.UUID;

public record RewindResponse(
        UUID sessionId,
        String title,
        int segmentIndex,
        String narration,
        List<Choice> choices,
        int livesRemaining,
        int livesTotal,
        int plannedSegments,
        List<String> disabledChoiceIds
) {}


