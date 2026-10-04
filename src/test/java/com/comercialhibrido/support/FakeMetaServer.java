package com.comercialhibrido.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Imitación mínima de la Graph API de Meta para tests: verificación de número,
 * listado de plantillas y envío de mensajes. Acepta solo el token {@link #TOKEN}.
 */
public class FakeMetaServer implements AutoCloseable {

    public static final String TOKEN = "token-valido";
    public static final String PHONE_ID = "1112223334445";
    public static final String WABA_ID = "222333444555";

    private final HttpServer server;
    private final List<String> sentPayloads = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    public FakeMetaServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Cuerpos JSON recibidos en POST /{phone}/messages, en orden. */
    public List<String> sentPayloads() {
        return sentPayloads;
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!("Bearer " + TOKEN).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            reply(ex, 401, "{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}");
            return;
        }
        if ("POST".equals(ex.getRequestMethod()) && path.endsWith("/messages")) {
            sentPayloads.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(ex, 200, "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.fake-" + counter.incrementAndGet() + "\"}]}");
            return;
        }
        if (path.equals("/" + WABA_ID + "/message_templates")) {
            reply(ex, 200, """
                {"data":[
                  {"name":"seguimiento","language":"es","status":"APPROVED","category":"MARKETING",
                   "components":[{"type":"BODY","text":"Hola {{1}}, ¿seguimos con tu pedido de {{2}}?"}]},
                  {"name":"saludo_simple","language":"es","status":"APPROVED","category":"UTILITY",
                   "components":[{"type":"HEADER","format":"TEXT","text":"Hola"},{"type":"BODY","text":"Gracias por escribirnos."}]},
                  {"name":"rechazada","language":"es","status":"REJECTED","category":"MARKETING",
                   "components":[{"type":"BODY","text":"No debería aparecer"}]}
                ]}""");
            return;
        }
        if (path.endsWith("/message_templates")) {
            reply(ex, 400, "{\"error\":{\"message\":\"Unsupported get request. Object does not exist\",\"code\":100}}");
            return;
        }
        if (path.equals("/" + PHONE_ID)) {
            reply(ex, 200, "{\"display_phone_number\":\"+57 300 000 0000\",\"verified_name\":\"Empresa de Prueba\",\"quality_rating\":\"GREEN\",\"id\":\"" + PHONE_ID + "\"}");
            return;
        }
        reply(ex, 400, "{\"error\":{\"message\":\"Object with ID does not exist\",\"code\":100}}");
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
