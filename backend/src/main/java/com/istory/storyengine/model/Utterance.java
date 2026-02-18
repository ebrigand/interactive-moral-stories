package com.istory.storyengine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Utterance {

    /**
     * Identifiant stable du locuteur (réutilisé si le même personnage reparle).
     * Exemples: "NARRATOR", "HERO", "MAMAN", "LUCAS", "RENARD"
     */
    private String speaker;

    /**
     * "CHILD" ou "ADULT"
     */
    private String ageGroup;

    /**
     * "MALE", "FEMALE" ou "NEUTRAL"
     */
    private String gender;

    /**
     * Texte à dire par ce locuteur, sans guillemets.
     */
    private String text;
}
