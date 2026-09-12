package com.comercialhibrido.service;

import com.comercialhibrido.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final int MAX_CONTEXT_CHARS = 120_000;

    private final AgentProperties agentProperties;

    public String load() {
        String configuredPath = agentProperties.knowledgeBaseFile();
        if (configuredPath == null || configuredPath.isBlank()) {
            return "No hay una base de conocimiento comercial configurada. "
                + "No inventes productos, precios, disponibilidad ni politicas y escala la consulta.";
        }

        try {
            String content = Files.readString(Path.of(configuredPath), StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                return "La base de conocimiento esta vacia. No inventes datos y escala la consulta.";
            }
            return content.substring(0, Math.min(content.length(), MAX_CONTEXT_CHARS));
        } catch (IOException | RuntimeException e) {
            log.error("No se pudo leer la base de conocimiento {}: {}", configuredPath, e.getMessage());
            return "La base de conocimiento no esta disponible. No inventes datos y escala la consulta.";
        }
    }
}
