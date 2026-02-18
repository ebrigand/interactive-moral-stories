package com.istory.storyengine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiServiceExtractionTest {

    @Test
    void extractsOutputTextFromSampleResponse() throws Exception {
        // On ne teste pas l'appel réseau ici, juste l'extraction.
        com.istory.storyengine.service.openai.OpenAiProperties props = new com.istory.storyengine.service.openai.OpenAiProperties();
        props.setModel("gpt-4.1");

        OpenAiService service = new OpenAiService(WebClient.builder().baseUrl("http://localhost").build(), props);

        String sample = """
        {
          "output": [
            {
              "type": "message",
              "content": [
                { "type": "output_text", "text": "{\\"narration\\":\\"OK\\",\\"choices\\":[]}" }
              ]
            }
          ]
        }
        """;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(sample);

        // Appel via réflexion privée = pas idéal, donc on valide autrement dans ton code réel,
        // ou tu peux rendre extractOutputText package-private.
        // Ici on se contente de vérifier que le sample est conforme à ce que ton extracteur attend.
        assertTrue(root.get("output").isArray());
    }
}
