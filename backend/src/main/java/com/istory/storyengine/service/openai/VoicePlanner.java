package com.istory.storyengine.service.openai;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class VoicePlanner {

    // Voix féminines enfant
    private static final List<String> CHILD_FEMALE_VOICES = List.of("nova", "shimmer");
    // Voix masculines enfant
    private static final List<String> CHILD_MALE_VOICES   = List.of("fable", "ash");
    // Voix féminines adulte
    private static final List<String> ADULT_FEMALE_VOICES = List.of("coral", "ballad");
    // Voix masculines adulte
    private static final List<String> ADULT_MALE_VOICES   = List.of("onyx", "echo");
    // Voix neutres (narrateur, genre inconnu)
    private static final List<String> NEUTRAL_VOICES      = List.of("alloy", "sage");

    private VoicePlanner() {}

    /**
     * Sélectionne une voix cohérente avec l'âge et le genre du locuteur.
     *
     * @param storySeed  graine stable de l'histoire (assure la cohérence entre segments)
     * @param speaker    identifiant du locuteur (ex: "HERO", "MAMAN", "RENARD")
     * @param ageGroup   "CHILD" ou "ADULT"
     * @param gender     "MALE", "FEMALE" ou "NEUTRAL" (null → NEUTRAL)
     * @param targetAge  âge cible pour les instructions enfant
     */
    public static VoiceSpec pick(String storySeed, String speaker, String ageGroup, String gender, int targetAge) {
        String key   = (storySeed == null ? "" : storySeed) + "|" + norm(speaker);
        boolean child = "CHILD".equalsIgnoreCase(ageGroup);
        String g      = norm(gender); // "MALE", "FEMALE" ou ""

        List<String> pool;
        String instructions;

        if (child) {
            if ("FEMALE".equals(g)) {
                pool         = CHILD_FEMALE_VOICES;
                instructions = "Parle comme une vraie petite fille d'environ " + targetAge + " ans. Voix naturelle, spontanée, légèrement aiguë. Pas de diction adulte professionnelle.";
            } else if ("MALE".equals(g)) {
                pool         = CHILD_MALE_VOICES;
                instructions = "Parle comme un vrai petit garçon d'environ " + targetAge + " ans. Voix naturelle, spontanée, légèrement aiguë. Pas de diction adulte professionnelle.";
            } else {
                pool         = CHILD_FEMALE_VOICES;
                instructions = "Parle comme un vrai enfant d'environ " + targetAge + " ans. Voix naturelle, spontanée, légèrement aiguë. Pas de diction adulte professionnelle.";
            }
        } else {
            if ("FEMALE".equals(g)) {
                pool         = ADULT_FEMALE_VOICES;
                instructions = "Voix féminine chaleureuse et expressive, adaptée aux histoires pour enfants.";
            } else if ("MALE".equals(g)) {
                pool         = ADULT_MALE_VOICES;
                instructions = "Voix masculine chaleureuse et expressive, adaptée aux histoires pour enfants.";
            } else {
                pool         = NEUTRAL_VOICES;
                instructions = "Voix de narrateur chaleureuse et expressive, adaptée aux enfants.";
            }
        }

        String voice = pickFrom(pool, key);
        return new VoiceSpec(voice, instructions);
    }

    private static String pickFrom(List<String> voices, String key) {
        int idx = Math.floorMod(hash(key), voices.size());
        return voices.get(idx);
    }

    private static int hash(String s) {
        return java.util.Arrays.hashCode(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String norm(String s) {
        return (s == null ? "" : s.trim().toUpperCase());
    }

    public record VoiceSpec(String voice, String instructions) {}
}