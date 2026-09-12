package com.comercialhibrido.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
    String knowledgeBaseFile,
    String panelToken,
    String panelAllowedOrigins,
    String whatsappAppSecret,
    Integer maxAttempts,
    Integer retryBaseSeconds,
    Integer staleJobMinutes,
    Integer workerFixedDelayMs
) {
    public int maxAttemptsOrDefault() {
        return maxAttempts == null || maxAttempts < 1 ? 5 : maxAttempts;
    }

    public int retryBaseSecondsOrDefault() {
        return retryBaseSeconds == null || retryBaseSeconds < 1 ? 10 : retryBaseSeconds;
    }

    public int staleJobMinutesOrDefault() {
        return staleJobMinutes == null || staleJobMinutes < 1 ? 10 : staleJobMinutes;
    }

    public int workerFixedDelayMsOrDefault() {
        return workerFixedDelayMs == null || workerFixedDelayMs < 250 ? 1000 : workerFixedDelayMs;
    }
}
