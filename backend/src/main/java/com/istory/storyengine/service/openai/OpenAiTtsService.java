package com.istory.storyengine.service.openai;

import com.istory.storyengine.model.StorySession;
import com.istory.storyengine.repository.StorySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenAiTtsService {

    private final WebClient openAiWebClient;
    private final StorySessionRepository sessionRepository;

    /**
     * ✅ Choix du modèle TTS :
     * - "tts-1-hd" : meilleure qualité “pure” très souvent (et supporte speed)
     * - "gpt-4o-mini-tts" : supporte "instructions" (accent/intonation), MAIS speed n'est pas supporté
     *
     * (Tu peux basculer en commentant/décommentant.)
     */
    // private static final String TTS_MODEL = "tts-1-hd";
    private static final String TTS_MODEL = "gpt-4o-mini-tts";

    private static final String DEFAULT_TTS_LOCALE = "fr-FR";

    // OpenAI: voix supportées par tts-1 / tts-1-hd (set “plus petit”) :contentReference[oaicite:2]{index=2}
    private static final Set<String> TTS1_VOICES = Set.of(
            "alloy", "ash", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer"
    );

    private static final double DEFAULT_SPEED = 1.0;
    private static final double MIN_SPEED = 0.25;
    private static final double MAX_SPEED = 4.0;

    private static final String FORCE_FRENCH_ADULT_INSTRUCTIONS = String.join("\n",
            "Tu es une voix off française native (France).",
            "Parle en français avec un accent de France (pas d'accent anglais).",
            "Prononce correctement les noms propres français.",
            "Diction naturelle, rythme fluide, articulation claire.",
            "Ne lis pas les balises/ponctuations inutiles, garde une diction naturelle."
    );

    private static final String FORCE_FRENCH_CHILD_INSTRUCTIONS = String.join("\n",
            "Tu parles en français avec un accent de France (pas d'accent anglais).",
            "Tu as une voix d'enfant : naturelle, spontanée, légèrement aiguë. Pas de diction adulte professionnelle.",
            "Prononce correctement les noms propres français.",
            "Ne lis pas les balises/ponctuations inutiles, garde une diction naturelle et spontanée."
    );

    private boolean modelSupportsInstructions(String model) {
        return model != null && model.startsWith("gpt-4o-mini-tts");
    }

    private boolean modelSupportsSpeed(String model) {
        return model != null && (model.equals("tts-1") || model.equals("tts-1-hd"));
    }

    private String sanitizeVoiceForModel(String model, String voiceFromPlanner) {
        String v = (voiceFromPlanner == null) ? "" : voiceFromPlanner.trim().toLowerCase();
        if (v.isEmpty()) v = "alloy";

        // Si on utilise tts-1/tts-1-hd, on force une voix autorisée
        if (modelSupportsSpeed(model)) {
            if (!TTS1_VOICES.contains(v)) {
                return "alloy";
            }
        }
        return v;
    }

    private String buildInstructions(String locale, String ageGroup, String plannerInstructions) {
        String effectiveLocale = (locale == null || locale.isBlank()) ? DEFAULT_TTS_LOCALE : locale.trim();

        boolean forceFrench = effectiveLocale.equalsIgnoreCase("fr-FR") || effectiveLocale.equalsIgnoreCase("fr");
        if (!forceFrench) return plannerInstructions;

        boolean child = "CHILD".equalsIgnoreCase(ageGroup);
        String base = child ? FORCE_FRENCH_CHILD_INSTRUCTIONS : FORCE_FRENCH_ADULT_INSTRUCTIONS;

        if (plannerInstructions == null || plannerInstructions.isBlank()) return base;
        return base + "\n" + plannerInstructions;
    }

    private double clampSpeed(Double speed) {
        double s = (speed == null) ? DEFAULT_SPEED : speed;
        if (Double.isNaN(s) || Double.isInfinite(s)) return DEFAULT_SPEED;
        if (s < MIN_SPEED) return MIN_SPEED;
        if (s > MAX_SPEED) return MAX_SPEED;
        return s;
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

        // Force ageGroup=CHILD si le locuteur est le héros (speaker="HERO" ou correspond au playerName)
        String effectiveAgeGroup = ageGroup;
        if ("HERO".equalsIgnoreCase(speaker)
                || (session.getPlayerName() != null && session.getPlayerName().equalsIgnoreCase(speaker))) {
            effectiveAgeGroup = "CHILD";
        }

        var spec = VoicePlanner.pick(session.getStorySeed(), speaker, effectiveAgeGroup, gender, session.getTargetAge());

        String safeText = (text == null) ? "" : text;
        if (safeText.isBlank()) {
            // Evite un 400 si texte vide
            return new byte[0];
        }

        double effectiveSpeed = clampSpeed(speed);

        Map<String, Object> body = new HashMap<>();
        body.put("model", TTS_MODEL);
        body.put("voice", sanitizeVoiceForModel(TTS_MODEL, spec.voice()));
        body.put("response_format", "mp3");
        body.put("input", safeText);

        // ✅ instructions uniquement pour gpt-4o-mini-tts
        if (modelSupportsInstructions(TTS_MODEL)) {
            body.put("instructions", buildInstructions(locale, effectiveAgeGroup, spec.instructions()));
        }

        // ✅ speed uniquement pour tts-1 / tts-1-hd (sinon 400 avec gpt-4o-mini-tts) :contentReference[oaicite:3]{index=3}
        if (modelSupportsSpeed(TTS_MODEL)) {
            body.put("speed", effectiveSpeed);
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
}
