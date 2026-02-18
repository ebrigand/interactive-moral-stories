package com.istory.storyengine.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_node")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryNode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    // null pour le 1er noeud (racine)
    @Column(nullable = true)
    private UUID parentNodeId;

    // null pour le 1er noeud (racine)
    @Column(nullable = true, length = 2000)
    private String choiceText;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int segmentIndex;

    @Lob
    @Column(nullable = false)
    private String segmentJson; // JSON complet renvoyé par l'IA (narration + choix)

    // ✅ conservé (utile pour rewind / logique morale si présente)
    @Column(nullable = false)
    private boolean moralSegment; // true si choix moral fait pour arriver à ce segment
}
