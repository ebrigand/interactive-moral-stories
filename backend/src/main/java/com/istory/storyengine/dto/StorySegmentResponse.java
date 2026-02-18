package com.istory.storyengine.dto;

import com.istory.storyengine.model.Choice;
import com.istory.storyengine.model.Utterance;

import java.util.List;
import java.util.UUID;

public record StorySegmentResponse(
        UUID sessionId,
        String title,
        String narration,
        List<Choice> choices,
        boolean ended,
        String explanation,
        int livesRemaining,
        int livesTotal,
        int segmentIndex,
        int plannedSegments,
        List<String> disabledChoiceIds,
        int chapterCount,
        List<Utterance> utterances
) {}




