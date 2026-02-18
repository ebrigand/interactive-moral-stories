package com.istory.storyengine.controller;

import com.istory.storyengine.dto.ChoiceRequest;
import com.istory.storyengine.dto.RewindResponse;
import com.istory.storyengine.dto.StartStoryRequest;
import com.istory.storyengine.dto.StorySegmentResponse;
import com.istory.storyengine.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/story")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping("/start")
    public StorySegmentResponse start(@Valid @RequestBody StartStoryRequest req) {
        return storyService.start(req);
    }

    @PostMapping("/{id}/choose")
    public StorySegmentResponse choose(@PathVariable UUID id, @Valid @RequestBody ChoiceRequest req) {
        return storyService.choose(id, req);
    }

    @PostMapping("/{id}/rewind")
    public RewindResponse rewind(@PathVariable UUID id) {
        return storyService.rewind(id);
    }
}
