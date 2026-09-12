package com.comercialhibrido.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public record OpenAiProperties(
    String apiBaseUrl,
    String apiKey,
    String model
) {}