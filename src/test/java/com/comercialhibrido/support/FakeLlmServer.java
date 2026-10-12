package com.comercialhibrido.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proveedor de IA simulado (API compatible con /chat/completions): devuelve la respuesta
 * estructurada que cada prueba configure y guarda los prompts recibidos.
 */
public class FakeLlmServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final AtomicReference<Map<String, Object>> answer = new AtomicReference<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger rateLimited = new java.util.concurrent.atomic.AtomicInteger();

    public FakeLlmServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/chat/completions", this::handle);
        server.createContext("/audio/transcriptions", this::transcribe);
        server.start();
        respond("Hola, ¿en qué te ayudo?", 10, false, "");
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private final AtomicReference<String> transcription = new AtomicReference<>("Hola, quiero pedir dos coronas para el viernes");
    private final List<String> transcriptionRequests = new CopyOnWriteArrayList<>();

    /** Texto que devolverá Whisper para la próxima nota de voz. */
    public void transcribeAs(String text) {
        transcription.set(text);
    }

    /** Formularios multipart recibidos en /audio/transcriptions (como texto ISO-8859-1). */
    public List<String> transcriptionRequests() {
        return transcriptionRequests;
    }

    private void transcribe(HttpExchange ex) throws IOException {
        transcriptionRequests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        byte[] bytes = JSON.writeValueAsBytes(Map.of("text", transcription.get()));
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Configura lo que "dirá" el modelo en la próxima llamada. */
    public void respond(String respuesta, int leadScore, boolean escalar, String resumen) {
        answer.set(Map.of("respuesta", respuesta, "leadScore", leadScore,
            "requiereEscalamiento", escalar, "resumenParaAsesor", resumen));
    }

    /** Las próximas {@code n} llamadas responden 429 (límite de uso), como el plan gratuito de Groq. */
    public void rateLimitNext(int n) {
        rateLimited.set(n);
    }

    /** Cuerpos JSON de las peticiones recibidas (incluyen el prompt completo). */
    public List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange ex) throws IOException {
        requests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (rateLimited.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            byte[] error = "{\"error\":{\"message\":\"Rate limit reached\"}}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.getResponseHeaders().add("retry-after", "1");
            ex.sendResponseHeaders(429, error.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(error);
            }
            return;
        }
        String content = JSON.writeValueAsString(answer.get());
        byte[] bytes = JSON.writeValueAsBytes(Map.of(
            "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content)))));
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
