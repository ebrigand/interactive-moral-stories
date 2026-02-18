package com.istory.storyengine.service.openai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {
    private String baseUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds = 30;
}
