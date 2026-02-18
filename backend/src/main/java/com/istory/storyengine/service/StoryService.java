package com.istory.storyengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.istory.storyengine.dto.ChoiceRequest;
import com.istory.storyengine.dto.RewindResponse;
import com.istory.storyengine.dto.StartStoryRequest;
import com.istory.storyengine.dto.StorySegmentResponse;
import com.istory.storyengine.model.*;
import com.istory.storyengine.repository.StoryNodeRepository;
import com.istory.storyengine.repository.StorySessionRepository;
import com.istory.storyengine.service.prompt.PromptBuilder;
import com.istory.storyengine.service.prompt.PromptUser;
import com.istory.storyengine.validation.StorySegmentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StorySessionRepository sessionRepository;
    private final StoryNodeRepository nodeRepository;

    private final PromptBuilder promptBuilder;
    private final OpenAiService openAiService;
    private final StorySegmentValidator validator;

    private final ObjectMapper mapper = new ObjectMapper();

    public StorySegmentResponse start(StartStoryRequest req) {

        StorySession session = new StorySession();
        session.setTargetAge(req.targetAge());
        session.setPlayerName(req.playerName());
        session.setTheme(req.theme());

        // ✅ durationMinutes -> chapterCount
        session.setChapterCount(req.chapterCount());

        // Defaults simples si le front n'envoie pas le reste
        session.setTone((req.tone() == null || req.tone().isBlank()) ? "Aventure et bienveillance" : req.tone());
        session.setMission((req.mission() == null || req.mission().isBlank()) ? "Vivre une aventure et aider quelqu’un" : req.mission());
        session.setEnvironment((req.environment() == null || req.environment().isBlank())
                ? "Un lieu original lié au thème : " + req.theme()
                : req.environment());

        // IMPORTANT : le héros = playerName
        session.setCharacter((req.character() == null || req.character().isBlank())
                ? ("Un enfant héroïque nommé " + req.playerName())
                : req.character());

        session.setImmoralChoicesCount(0);
        session.setCurrentSegmentIndex(0);
        session.setLastMoralSegmentIndex(0);
        session.setStatus(StoryStatus.RUNNING);

        // 🔥 Variété forte, cohérente par session via storySeed
        String seed = newSeed();
        session.setStorySeed(seed);

        Random rng = new Random(seed.hashCode());

        session.setOpeningStyle(pickOpeningStyle(rng));
        session.setVariationPack(pickVariationPack(rng, req.theme(), req.targetAge(), req.chapterCount()));
        session.setAvoidListCsv(buildAvoidListCsv(rng, req.theme()));

        // Titre via OpenAI (avec seed + variationPack)
        String title = generateTitle(session, req);
        session.setTitle(title);

        session = sessionRepository.save(session);

        String lastChoiceSummary = "Début de l'histoire : lancement de l'aventure.";
        return generateAndPersistSegment(session, lastChoiceSummary, true);
    }

    private String generateTitle(StorySession session, StartStoryRequest req) {
        try {
            String contextJson = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
                put("targetAge", req.targetAge());
                put("playerName", req.playerName());
                put("theme", req.theme());
                put("chapterCount", req.chapterCount());

                put("character", session.getCharacter());
                put("environment", session.getEnvironment());
                put("mission", session.getMission());
                put("tone", session.getTone());

                // 🔥 Variété
                put("storySeed", session.getStorySeed());
                put("openingStyle", session.getOpeningStyle());
                put("variationPack", session.getVariationPack());
                put("avoidList", splitAvoidList(session.getAvoidListCsv()));
            }});

            String titleJson = openAiService.generateTitleJson(contextJson);

            var node = mapper.readTree(titleJson);
            String title = node.path("title").asText("").trim();

            if (title.isBlank()) return "Une aventure surprenante";

            // mobile-friendly
            if (title.length() > 46) title = title.substring(0, 46).trim();

            return title;

        } catch (Exception e) {
            return "Une aventure surprenante";
        }
    }

    public StorySegmentResponse choose(UUID sessionId, ChoiceRequest req) {

        StorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session not found"));

        if (session.getStatus() != StoryStatus.RUNNING) {
            throw new IllegalStateException("Story ended. Use /rewind to continue from last moral checkpoint.");
        }

        int currentIndex = session.getCurrentSegmentIndex();

        StoryNode node = nodeRepository.findFirstBySessionIdAndSegmentIndex(sessionId, currentIndex)
                .orElseThrow(() -> new IllegalStateException("No segment found at index=" + currentIndex));

        final StorySegment lastSegment;
        try {
            lastSegment = mapper.readValue(node.getSegmentJson(), StorySegment.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse segment json at index=" + currentIndex, e);
        }

        if (lastSegment.isEnded()) {
            throw new IllegalStateException("Cannot choose on an ended segment");
        }

        String choiceId = req.choice() != null ? req.choice().trim() : null;
        if (choiceId == null || choiceId.isBlank()) {
            throw new IllegalStateException("choiceId is blank");
        }

        Choice chosen = lastSegment.getChoices().stream()
                .filter(c -> c.getId() != null && c.getId().equalsIgnoreCase(choiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Invalid choice id: " + choiceId));

        boolean isMoralChoice =
                lastSegment.getMoralChoiceIds() != null
                        && lastSegment.getMoralChoiceIds().stream()
                        .anyMatch(id -> id.equalsIgnoreCase(choiceId));

        // ✅ mise à jour session
        if (isMoralChoice) {
            session.setLastMoralSegmentIndex(currentIndex);
            session.setStatus(StoryStatus.RUNNING);

        } else {
            session.setImmoralChoicesCount(session.getImmoralChoicesCount() + 1);
            session.setStatus(StoryStatus.FAILED);

            // ✅ vies
            session.setLivesRemaining(Math.max(0, session.getLivesRemaining() - 1));

            // ✅ mémorise l’échec pour griser
            session.setLastFailedSegmentIndex(currentIndex);
            session.setLastFailedChoiceId(choiceId);
        }

        // ✅ on avance pour générer le segment suivant
        session.setCurrentSegmentIndex(currentIndex + 1);
        sessionRepository.save(session);

        String lastChoiceSummary = isMoralChoice
                ? "Le héros a choisi une voie juste, même si elle demande un effort."
                : ("Le héros a choisi une option tentante à court terme (" + safe(chosen.getText()) + "), mais cela a un coût moral.");

        return generateAndPersistSegment(session, lastChoiceSummary, isMoralChoice);
    }


    @Transactional
    public RewindResponse rewind(UUID sessionId) {
        StorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session not found"));

        Integer failedIndex = session.getLastFailedSegmentIndex();
        String failedChoiceId = session.getLastFailedChoiceId();

        if (failedIndex == null) {
            // fallback: si pas d'échec enregistré, retourne au dernier moral
            failedIndex = session.getLastMoralSegmentIndex();
        }

        final int idx = failedIndex;

        StoryNode node = nodeRepository.findFirstBySessionIdAndSegmentIndex(sessionId, idx)
                .orElseThrow(() -> new IllegalStateException("No node found at index=" + idx));

        // supprime tout ce qui est après ce segment
        nodeRepository.deleteBySessionIdAndSegmentIndexGreaterThan(sessionId, failedIndex);

        try {
            StorySegment segment = mapper.readValue(node.getSegmentJson(), StorySegment.class);

            session.setCurrentSegmentIndex(failedIndex);
            session.setStatus(StoryStatus.RUNNING);
            sessionRepository.save(session);

            List<String> disabled = List.of();
            if (failedChoiceId != null && !failedChoiceId.isBlank()
                    && session.getLastFailedSegmentIndex() != null
                    && session.getLastFailedSegmentIndex() == failedIndex) {
                disabled = List.of(failedChoiceId);
            }

            return new RewindResponse(
                    sessionId,
                    session.getTitle(),
                    failedIndex,
                    segment.getNarration(),
                    segment.getChoices(),
                    session.getLivesRemaining(),
                    session.getLivesTotal(),
                    session.getPlannedSegments(),
                    disabled
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to rewind", e);
        }
    }


    private StorySegmentResponse generateAndPersistSegment(
            StorySession session,
            String lastChoiceSummary,
            boolean arrivedFromMoralChoice
    ) {
        try {
            boolean failureImminent = (session.getStatus() == StoryStatus.FAILED);

            // ✅ plannedSegments = chapterCount (une seule fois)
            if (session.getPlannedSegments() <= 0) {
                int planned = Math.max(1, session.getChapterCount());
                session.setPlannedSegments(planned);
            }

            // ✅ lives calculées une seule fois (et livesRemaining initialisée une seule fois)
            if (session.getLivesTotal() <= 0) {
                int livesTotal = Math.max(2, (int) Math.ceil(session.getPlannedSegments() / 2.0)); // 1 vie / 2 chapitres (≈)
                session.setLivesTotal(livesTotal);

                if (session.getLivesRemaining() <= 0) {
                    session.setLivesRemaining(livesTotal);
                }
            }

            // ⚠️ Important : on persiste ces champs si on vient de les initialiser
            sessionRepository.save(session);

            // ❌ IMPORTANT: NE PAS reset lastFailed* ici

            var avoid = splitAvoidList(session.getAvoidListCsv());

            PromptUser promptUser = new PromptUser(
                    session.getTargetAge(),
                    session.getPlayerName(),
                    session.getTheme(),
                    session.getChapterCount(),          // ✅ chapterCount
                    session.getPlannedSegments(),       // ✅ plannedSegments

                    session.getCharacter(),
                    session.getEnvironment(),
                    session.getMission(),
                    session.getTone(),
                    session.getTitle(),

                    session.getStorySeed(),
                    session.getOpeningStyle(),
                    session.getVariationPack(),
                    avoid,

                    session.getCurrentSegmentIndex(),
                    session.getImmoralChoicesCount(),
                    lastChoiceSummary,
                    failureImminent
            );

            String userPromptJson = promptBuilder.build(promptUser);

            for (int attempt = 1; attempt <= 2; attempt++) {

                String extraStrict = "";
                if (failureImminent && attempt == 2) {
                    extraStrict =
                            "\n\nIMPORTANT: TU DOIS TERMINER MAINTENANT. " +
                                    "Retourne ended=true, choices=[], moralChoiceIds=[], et explanation non vide. " +
                                    "La narration doit montrer une conséquence claire du mauvais choix puis STOP.";
                }

                String segmentJson = openAiService.generateJsonText(userPromptJson + extraStrict);

                StorySegment segment = mapper.readValue(segmentJson, StorySegment.class);

                normalizeUtterances(segment, session.getPlayerName());

                if (failureImminent && !segment.isEnded()) {
                    if (attempt == 1) continue;
                    throw new IllegalStateException("AI did not end story while failureImminent=true. raw=" + segmentJson);
                }

                validator.validate(segment);

                // ✅ createdAt est NOT NULL dans StoryNode -> on le renseigne
                nodeRepository.save(StoryNode.builder()
                        .sessionId(session.getId())
                        .parentNodeId(null)
                        .choiceText(null)
                        .createdAt(java.time.Instant.now())
                        .segmentIndex(session.getCurrentSegmentIndex())
                        .segmentJson(segmentJson)
                        .moralSegment(arrivedFromMoralChoice)
                        .build());

                if (segment.isEnded()) {
                    session.setStatus(StoryStatus.FAILED);
                    sessionRepository.save(session);
                }

                return new StorySegmentResponse(
                        session.getId(),
                        session.getTitle(),
                        segment.getNarration(),
                        segment.getChoices(),
                        segment.isEnded(),
                        segment.getExplanation() == null ? "" : segment.getExplanation(),
                        session.getLivesRemaining(),
                        session.getLivesTotal(),
                        session.getCurrentSegmentIndex(),
                        session.getPlannedSegments(),
                        List.of(),
                        session.getChapterCount(),
                        segment.getUtterances() == null ? List.of() : segment.getUtterances()
                );
            }

            throw new IllegalStateException("Unreachable generate loop");

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate story segment", e);
        }
    }

    /**
     * Normalise les champs de chaque Utterance après désérialisation du JSON de l'IA :
     * - Force ageGroup="CHILD" pour le héros (speaker=HERO ou playerName)
     * - Défaut ageGroup="ADULT" si absent/vide
     * - Normalise gender en majuscules ; défaut "NEUTRAL" si invalide/absent
     */
    private void normalizeUtterances(StorySegment segment, String playerName) {
        if (segment.getUtterances() == null) return;

        for (Utterance u : segment.getUtterances()) {
            if (u == null) continue;

            String speaker = u.getSpeaker() == null ? "" : u.getSpeaker().trim();

            // Force CHILD pour le héros
            boolean isHero = "HERO".equalsIgnoreCase(speaker)
                    || (playerName != null && playerName.equalsIgnoreCase(speaker));
            if (isHero) {
                u.setAgeGroup("CHILD");
            } else if (u.getAgeGroup() == null || u.getAgeGroup().isBlank()) {
                u.setAgeGroup("ADULT");
            }

            // Normalise gender
            String g = u.getGender() == null ? "" : u.getGender().trim().toUpperCase();
            if (!g.equals("MALE") && !g.equals("FEMALE") && !g.equals("NEUTRAL")) {
                g = "NEUTRAL";
            }
            u.setGender(g);
        }
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    // -------------------------
    // 🔥 Variabilité (cohérente)
    // -------------------------

    private String newSeed() {
        return UUID.randomUUID().toString();
    }

    private String pickOpeningStyle(Random rng) {
        String[] styles = {
                "journal intime",
                "dialogue",
                "message secret",
                "bulletin radio",
                "enquête",
                "conte",
                "carte au trésor",
                "in medias res",
                "rumeur",
                "défi",
                "lettre",
                "rêve étrange",
                "annonce au micro",
                "petite scène de théâtre",
                "plan de mission"
        };
        return styles[rng.nextInt(styles.length)];
    }

    private String pickVariationPack(Random rng, String theme, int age, int chapterCount) {
        String[] eras = {
                "aujourd'hui", "futur proche", "moyen âge", "monde sous-marin",
                "station spatiale", "ville volante", "désert de cristal",
                "île mécanique", "musée vivant", "train magique"
        };

        String[] twists = {
                "un allié inattendu", "une règle magique", "un secret à protéger",
                "un malentendu", "un objet étrange", "un lieu qui change",
                "un double objectif", "un piège moral subtil", "un personnage ambigu"
        };

        String[] obstacles = {
                "énigme", "négociation", "courage", "coopération",
                "patience", "observation", "créativité", "empathie", "prudence"
        };

        String[] styles = {
                "vif", "drôle", "poétique", "suspense doux", "épique", "mystérieux", "tendre"
        };

        String era = eras[rng.nextInt(eras.length)];
        String twist = twists[rng.nextInt(twists.length)];
        String obstacle = obstacles[rng.nextInt(obstacles.length)];
        String style = styles[rng.nextInt(styles.length)];

        String humour = (age <= 7 ? "léger" : "modéré");

        // ✅ plannedSegments = chapterCount (pas de conversion /4)
        int plannedSegments = Math.max(1, chapterCount);

        return "theme=" + theme +
                "; epoque=" + era +
                "; twist=" + twist +
                "; obstacle_cle=" + obstacle +
                "; style=" + style +
                "; humour=" + humour +
                "; segments=" + plannedSegments;
    }

    private String buildAvoidListCsv(Random rng, String theme) {
        List<String> global = new ArrayList<>(List.of(
                "renard blessé",
                "forêt mystérieuse",
                "petit explorateur curieux",
                "rayons de soleil à travers les arbres",
                "soudain un bruit étrange",
                "un vieux chêne",
                "un filet de pêche",
                "un animal coincé",
                "tu marches doucement",
                "grands yeux brillants"
        ));

        // Ajoute quelques bans “template”
        List<String> extra = List.of(
                "un sac d’aventurier contient",
                "tu entends des oiseaux chanter",
                "un écureuil te regarde",
                "motifs dorés sur le sol"
        );

        // on tire 4 de extra au hasard + 6 de global
        Collections.shuffle(global, rng);
        List<String> picked = new ArrayList<>(global.subList(0, Math.min(6, global.size())));

        List<String> extraShuffled = new ArrayList<>(extra);
        Collections.shuffle(extraShuffled, rng);
        picked.addAll(extraShuffled.subList(0, Math.min(4, extraShuffled.size())));

        // mini contrainte liée au theme : éviter de répéter le thème “sans le vouloir”
        if (theme != null && !theme.isBlank()) {
            picked.add("forêt"); // évite le fallback forêt si thème différent
        }

        return picked.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining("|"));
    }

    private List<String> splitAvoidList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }
}
