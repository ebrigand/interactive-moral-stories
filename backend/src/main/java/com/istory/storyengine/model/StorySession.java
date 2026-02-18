package com.istory.storyengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Entity
@Table(name = "story_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Check(constraints = "chapter_count BETWEEN 4 AND 60")
public class StorySession {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private int targetAge;

    @Column(nullable = false, length = 2000)
    private String character;

    @Column(nullable = false, length = 2000)
    private String environment;

    @Column(nullable = false, length = 2000)
    private String mission;

    @Column(nullable = false, length = 2000)
    private String tone;

    @Column(nullable = false)
    private int currentSegmentIndex = 0;

    @Column(nullable = false)
    private int lastMoralSegmentIndex = 0;

    @Column(nullable = false)
    private int immoralChoicesCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoryStatus status = StoryStatus.RUNNING;

    @Column(nullable = false, length = 80)
    private String playerName;

    @Column(nullable = false, length = 80)
    private String theme;

    // ✅ remplace durationMinutes
    @Column(name = "chapter_count", nullable = false)
    private int chapterCount;

    // Variabilité / anti-répétition
    @Column(nullable = false, length = 36)
    private String storySeed;

    @Column(nullable = false, length = 40)
    private String openingStyle;

    @Column(nullable = false, length = 1000)
    private String variationPack;

    @Column(nullable = false, length = 800)
    private String avoidListCsv;

    // Game
    @Column(nullable = false)
    private int livesTotal;

    @Column(nullable = false)
    private int livesRemaining;

    // Dernier échec pour griser l’option au rewind
    @Column(nullable = true)
    private Integer lastFailedSegmentIndex;

    @Column(nullable = true, length = 5)
    private String lastFailedChoiceId;

    // Affichage “Chapitre X/Y”
    @Column(nullable = false)
    private int plannedSegments;
}
