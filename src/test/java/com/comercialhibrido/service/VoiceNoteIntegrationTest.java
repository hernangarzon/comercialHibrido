package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.support.FakeLlmServer;
import com.comercialhibrido.support.FakeMetaServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notas de voz: llegan por el webhook, se descargan de Meta, se transcriben con Whisper
 * y el bot responde a lo que dijo el cliente (o le pide escribir si no se entendió).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoiceNoteIntegrationTest {

    private static final FakeMetaServer META = new FakeMetaServer();
    private static final FakeLlmServer LLM = new FakeLlmServer();

    @DynamicPropertySource
    static void fakes(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.api-base-url", META::baseUrl);
        registry.add("whatsapp.access-token", () -> FakeMetaServer.TOKEN);
        registry.add("openai.api-base-url", LLM::baseUrl);
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:voz;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
    }

    @AfterAll
    static void stop() {
        META.close();
        LLM.close();
    }

    @Autowired MockMvc mvc;
    @Autowired CompanyRepository companyRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired BotResponseService botResponseService;

    private String phoneId;

    @BeforeEach
    void preparar() {
        phoneId = "9" + Math.abs(UUID.randomUUID().hashCode());
        companyRepository.save(Company.builder().name("Empresa audio").whatsappPhoneNumberId(phoneId).build());
    }

    @Test
    void notaDeVozSeTranscribeYElBotRespondeALoQueDijo() throws Exception {
        LLM.transcribeAs("Hola, quiero pedir dos coronas para el viernes");
        LLM.respond("¡Claro! Dos coronas para el viernes. Un asesor confirma tu pedido.", 90, true, "Quiere 2 coronas para el viernes.");

        Message audio = recibirAudio(FakeMetaServer.AUDIO_MEDIA_ID);
        assertThat(audio.getMediaType()).isEqualTo("AUDIO");
        assertThat(audio.getContent()).isEqualTo(VoiceNotes.PENDING);

        botResponseService.generarYEncolarRespuesta(audio.getConversation().getId());

        Message transcrito = messageRepository.findById(audio.getId()).orElseThrow();
        assertThat(transcrito.getContent()).isEqualTo("Hola, quiero pedir dos coronas para el viernes");

        String formulario = LLM.transcriptionRequests().getLast();
        assertThat(formulario).contains("whisper-large-v3-turbo").contains("nota-de-voz.ogg").contains("OggS-nota-de-voz-simulada");

        assertThat(LLM.requests().getLast()).contains("(Nota de voz) Hola, quiero pedir dos coronas para el viernes");
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(audio.getConversation().getId()))
            .anyMatch(m -> m.getSender() == MessageSender.BOT);
    }

    @Test
    void siNoSePuedeDescargarElAudioElBotPideQueLoEscriba() throws Exception {
        LLM.respond("No pude escuchar tu audio, ¿me lo escribes?", 10, false, "");

        Message audio = recibirAudio("audio-inexistente");
        botResponseService.generarYEncolarRespuesta(audio.getConversation().getId());

        assertThat(messageRepository.findById(audio.getId()).orElseThrow().getContent()).isEqualTo(VoiceNotes.FAILED);
        assertThat(LLM.requests().getLast()).contains("no se pudo entender");
    }

    private Message recibirAudio(String mediaId) throws Exception {
        String wamid = "wamid.audio-" + UUID.randomUUID();
        String from = "57" + Math.abs(wamid.hashCode());
        byte[] body = ("""
            {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"%s"},
              "contacts":[{"wa_id":"%s","profile":{"name":"Cliente Audio"}}],
              "messages":[{"from":"%s","id":"%s","type":"audio",
                "audio":{"id":"%s","mime_type":"audio/ogg; codecs=opus","voice":true}}]}}]}]}
            """.formatted(phoneId, from, from, wamid, mediaId)).getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", "sha256=" + HexFormat.of().formatHex(mac.doFinal(body))))
            .andExpect(status().isOk());
        return messageRepository.findByWhatsappMessageId(wamid).orElseThrow();
    }
}
