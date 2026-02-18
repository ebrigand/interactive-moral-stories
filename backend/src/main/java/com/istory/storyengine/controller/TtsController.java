package com.istory.storyengine.controller;

import com.istory.storyengine.dto.TtsUtteranceRequest;
import com.istory.storyengine.service.openai.OpenAiTtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final OpenAiTtsService tts;

    @PostMapping(value = "/{sessionId}/utterance", produces = "audio/mpeg")
    public @ResponseBody byte[] utterance(
            @PathVariable UUID sessionId,
            @RequestParam(name = "locale", defaultValue = "fr-FR") String locale,
            @Valid @RequestBody TtsUtteranceRequest req
    ) {
        // speed is optional; default handled in service
        return tts.synthesizeUtterance(
                sessionId,
                req.speaker(),
                req.ageGroup(),
                req.gender(),
                req.text(),
                req.speed(),
                locale
        );
    }
}
