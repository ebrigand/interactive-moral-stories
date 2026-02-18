package com.istory.storyengine.service.openai;

import com.istory.storyengine.model.StorySession;
import com.istory.storyengine.repository.StorySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenAiTtsService {

    private final WebClient openAiWebClient;
    private final StorySessionRepository sessionRepository;

    // ✅ Narration stable + speed
    private static final String NARRATOR_MODEL = "tts-1-hd";

    // ✅ Dialogues + options : instructions (accent verrouillé)
    private static final String INSTRUCT_MODEL = "gpt-4o-mini-tts";

    // ✅ alloy exclu
    private static final String[] TTS_VOICES = {
            "sage"
    };

    private static final double DEFAULT_SPEED = 1.0;
    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 4.0;

    // ✅ Accent FR verrouillé (adulte)
    private static final String FR_ADULT_LOCKED = String.join("\n",
            "Tu parles en français avec un accent de France (France métropolitaine uniquement).",
            "Aucun accent anglais. Aucun accent québécois/belge/suisse/africain.",
            "Diction naturelle, articulée, stable.",
            "Ne change pas d'accent entre les phrases."
    );

    // ✅ Accent FR verrouillé (enfant)
    private static final String FR_CHILD_LOCKED = String.join("\n",
            "Tu parles en français avec un accent de France (France métropolitaine uniquement).",
            "Voix d'enfant naturelle, légère, spontanée (pas caricaturale).",
            "Aucun accent anglais. Aucun accent québécois/belge/suisse/africain.",
            "Diction stable, ne change pas d'accent entre les phrases."
    );

    private boolean modelSupportsSpeed(String model) {
        return model.equals("tts-1") || model.equals("tts-1-hd");
    }

    private double clampSpeed(Double speed) {
        double s = (speed == null) ? DEFAULT_SPEED : speed;
        if (Double.isNaN(s) || Double.isInfinite(s)) return DEFAULT_SPEED;
        if (s < MIN_SPEED) return MIN_SPEED;
        if (s > MAX_SPEED) return MAX_SPEED;
        return s;
    }

    private boolean isNarrator(String speaker) {
        return "NARRATOR".equalsIgnoreCase(speaker);
    }

    // ✅ Pour les options on utilise un speaker dédié
    private boolean isChoiceNarrator(String speaker) {
        return "CHOICE_NARRATOR".equalsIgnoreCase(speaker);
    }

    private boolean isHero(StorySession session, String speaker) {
        if ("HERO".equalsIgnoreCase(speaker)) return true;
        String player = session.getPlayerName();
        return player != null && !player.isBlank() && player.trim().equalsIgnoreCase(speaker);
    }

    // Narrateur stable par session
    private String pickStableNarratorVoice(UUID sessionId) {
        int idx = Math.abs(sessionId.hashCode()) % TTS_VOICES.length;
        return TTS_VOICES[idx];
    }

    // Voix stable par session + speaker
    private String pickStableVoice(UUID sessionId, String speaker) {
        String key = sessionId.toString() + "|" + (speaker == null ? "" : speaker.trim().toLowerCase());
        int idx = Math.abs(key.hashCode()) % TTS_VOICES.length;
        return TTS_VOICES[idx];
    }

    public byte[] synthesizeUtterance(
            UUID sessionId,
            String speaker,
            String ageGroup,
            String gender,
            String text,
            Double speed,
            String locale
    ) {
        StorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session not found"));

        String safeText = (text == null) ? "" : text;
        if (safeText.isBlank()) return new byte[0];

        String speakerNorm = (speaker == null) ? "" : speaker.trim();

        // =========================
        // 🎙 NARRATION (stable)
        // =========================
        if (isNarrator(speakerNorm)) {
            String narratorVoice = pickStableNarratorVoice(sessionId);

            Map<String, Object> body = new HashMap<>();
            body.put("model", NARRATOR_MODEL);
            body.put("voice", narratorVoice);
            body.put("response_format", "mp3");
            body.put("input", safeText);

            if (modelSupportsSpeed(NARRATOR_MODEL)) {
                body.put("speed", clampSpeed(speed));
            }

            return openAiWebClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        }

        // =========================
        // 🧾 OPTIONS : gpt-4o-mini-tts + FR verrouillé
        // =========================
        if (isChoiceNarrator(speakerNorm)) {

            String v = pickStableVoice(sessionId, "CHOICE_NARRATOR");

            Map<String, Object> body = new HashMap<>();
            body.put("model", INSTRUCT_MODEL);
            body.put("voice", v);
            body.put("response_format", "mp3");
            body.put("input", safeText);
            body.put("instructions", FR_ADULT_LOCKED);

            return openAiWebClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        }

        // =========================
        // 🎭 DIALOGUES : gpt-4o-mini-tts + FR verrouillé
        // =========================
        boolean hero = isHero(session, speakerNorm);
        String effectiveAge = hero ? "CHILD" : (ageGroup == null ? "ADULT" : ageGroup.trim());

        String dialogueVoice = pickStableVoice(sessionId, speakerNorm);

        Map<String, Object> body = new HashMap<>();
        body.put("model", INSTRUCT_MODEL);
        body.put("voice", dialogueVoice);
        body.put("response_format", "mp3");
        body.put("input", safeText);
        body.put("instructions", hero ? FR_CHILD_LOCKED : FR_ADULT_LOCKED);

        return openAiWebClient.post()
                .uri("/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.valueOf("audio/mpeg"))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }
}
