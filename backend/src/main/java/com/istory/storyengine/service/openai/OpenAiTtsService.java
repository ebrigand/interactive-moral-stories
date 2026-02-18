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

    // ✅ Modèle stable pour le narrateur
    private static final String NARRATOR_MODEL = "tts-1-hd";

    // ✅ Modèle expressif pour dialogues
    private static final String DIALOGUE_MODEL = "gpt-4o-mini-tts";

    private static final String DEFAULT_TTS_LOCALE = "fr-FR";

    // Voix supportées par tts-1 / tts-1-hd
    private static final String[] TTS1_VOICES = {
            "alloy", "ash", "coral", "echo", "onyx", "nova", "sage", "shimmer"
    };

    private static final double DEFAULT_SPEED = 1.0;
    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 4.0;

    private static final String FORCE_FRENCH_CHILD_INSTRUCTIONS = String.join("\n",
            "Tu parles en français avec un accent de France.",
            "Tu as une voix d'enfant naturelle et spontanée.",
            "Prononce correctement les noms propres français."
    );

    private static final String FORCE_FRENCH_ADULT_INSTRUCTIONS = String.join("\n",
            "Tu es une voix française naturelle.",
            "Accent de France.",
            "Diction claire et fluide."
    );

    private boolean modelSupportsInstructions(String model) {
        return model.startsWith("gpt-4o-mini-tts");
    }

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

    private boolean isHero(StorySession session, String speaker) {
        if ("HERO".equalsIgnoreCase(speaker)) return true;
        String player = session.getPlayerName();
        return player != null && !player.isBlank()
                && player.trim().equalsIgnoreCase(speaker);
    }

    private boolean isNarrator(String speaker) {
        return "NARRATOR".equalsIgnoreCase(speaker);
    }

    // ✅ Voix narrateur stable basée sur le storySeed
    private String pickStableNarratorVoice(UUID sessionId) {
        int hash = Math.abs(sessionId.hashCode());
        int index = hash % TTS1_VOICES.length;
        return TTS1_VOICES[index];
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

        // ================================
        // 🎙 NARRATEUR (stable pour toute l'histoire)
        // ================================
        if (isNarrator(speakerNorm)) {

            String narratorVoice = pickStableNarratorVoice(sessionId);

            Map<String, Object> body = new HashMap<>();
            body.put("model", NARRATOR_MODEL);
            body.put("voice", narratorVoice);
            body.put("response_format", "mp3");
            body.put("input", safeText);
            body.put("speed", clampSpeed(speed)); // speed supporté ici

            return openAiWebClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        }

        // ================================
        // 🎭 DIALOGUES
        // ================================
        String effectiveAgeGroup = ageGroup;

        if (isHero(session, speakerNorm)) {
            effectiveAgeGroup = "CHILD";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", DIALOGUE_MODEL);
        body.put("voice", "alloy"); // base voice pour dialogues
        body.put("response_format", "mp3");
        body.put("input", safeText);

        if (modelSupportsInstructions(DIALOGUE_MODEL)) {

            boolean isFrench = locale == null
                    || locale.isBlank()
                    || locale.equalsIgnoreCase("fr")
                    || locale.equalsIgnoreCase("fr-FR");

            if (isFrench) {
                String instructions = "CHILD".equalsIgnoreCase(effectiveAgeGroup)
                        ? FORCE_FRENCH_CHILD_INSTRUCTIONS
                        : FORCE_FRENCH_ADULT_INSTRUCTIONS;

                body.put("instructions", instructions);
            }
        }

        // ⚠️ PAS de speed ici (sinon 400)

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
