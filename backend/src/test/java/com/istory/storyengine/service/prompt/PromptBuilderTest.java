package com.istory.storyengine.service.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsJson() throws Exception {
        PromptUser user = new PromptUser(
                8,
                "Jeune explorateur",
                "Forêt mystérieuse",
                "Aider quelqu’un en danger",
                "Aventure bienveillante",
                0,
                0,
                "Début de l'histoire",
                false
        );

        String json = builder.build(user);

        // DEBUG utile si ça casse encore
        System.out.println("PROMPT JSON = " + json);

        JsonNode root = mapper.readTree(json);

        Assertions.assertTrue(root.has("storyContext"), "Missing storyContext");
        Assertions.assertTrue(root.has("storyState"), "Missing storyState");

        Assertions.assertEquals(8, root.path("storyContext").path("targetAge").asInt());
        Assertions.assertEquals(0, root.path("storyState").path("segmentIndex").asInt());
    }
}
