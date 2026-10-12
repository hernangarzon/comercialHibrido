package com.comercialhibrido.service;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Customer;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.domain.event.ConversationEscalatedEvent;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.CustomerRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.support.FakeLlmServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del bot: atiende siempre (también con la conversación escalada), solo escala
 * ventas concretas / pedido de humano / reclamo según el modelo, no repite la alerta,
 * y retoma la conversación si el asesor que la tomó no responde.
 */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class BotBehaviorIntegrationTest {

    private static final FakeLlmServer LLM = new FakeLlmServer();

    @DynamicPropertySource
    static void llm(DynamicPropertyRegistry registry) {
        registry.add("openai.api-base-url", LLM::baseUrl);
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:bot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
    }

    @AfterAll
    static void stop() {
        LLM.close();
    }

    @Autowired BotResponseService botResponseService;
    @Autowired ConversationStateService stateService;
    @Autowired CompanyRepository companyRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired ApplicationEvents events;

    private Company empresa;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().name("Empresa").build());
    }

    @Test
    void unScoreAltoSinVentaConcretaNoEscala() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        LLM.respond("La corona cuesta $45 USD. ¿Cuántas necesitas?", 90, false, "");

        botResponseService.generarYEncolarRespuesta(conv.getId());

        Conversation despues = recargar(conv);
        assertThat(despues.getStatus()).isEqualTo(ConversationStatus.BOT_ACTIVO);
        assertThat(despues.getLeadScore()).isEqualTo(90);
        assertThat(respuestasDelBot(conv)).isEqualTo(1);
        assertThat(events.stream(ConversationEscalatedEvent.class)).isEmpty();

        String prompt = LLM.requests().getLast();
        assertThat(prompt).contains("VENTA CONCRETA").contains("NO escales");
    }

    @Test
    void ventaConcretaEscalaConResumenYElBotSigueAtendiendoSinRepetirLaAlerta() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        LLM.respond("¡Perfecto! Un asesor confirma tu pedido en breve.", 95, true, "Quiere 1 corona de zirconio para mañana; falta el pago.");

        botResponseService.generarYEncolarRespuesta(conv.getId());

        Conversation escalada = recargar(conv);
        assertThat(escalada.getStatus()).isEqualTo(ConversationStatus.ESCALADO_PENDIENTE);
        assertThat(escalada.getSummary()).isEqualTo("Quiere 1 corona de zirconio para mañana; falta el pago.");
        Instant primeraEscalacion = escalada.getEscalatedAt();
        assertThat(primeraEscalacion).isNotNull();
        assertThat(events.stream(ConversationEscalatedEvent.class)).hasSize(1);

        // El cliente insiste mientras espera al asesor: el bot le responde y no hay alerta nueva.
        mensajeCliente(conv, "Por favor");
        LLM.respond("Ya avisé al asesor; mientras tanto, ¿te ayudo con algo más?", 95, true, "Sigue esperando confirmación.");
        botResponseService.generarYEncolarRespuesta(conv.getId());

        Conversation despues = recargar(conv);
        assertThat(respuestasDelBot(conv)).isEqualTo(2);
        assertThat(despues.getStatus()).isEqualTo(ConversationStatus.ESCALADO_PENDIENTE);
        assertThat(despues.getEscalatedAt()).isEqualTo(primeraEscalacion);
        assertThat(events.stream(ConversationEscalatedEvent.class)).hasSize(1);
        assertThat(LLM.requests().getLast()).contains("Ya se avisó a un asesor humano");
    }

    @Test
    void conUnAsesorActivoElBotNoResponde() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        stateService.tomarControl(conv.getId(), UUID.randomUUID());
        mensajeCliente(conv, "¿Sigues ahí?");

        botResponseService.generarYEncolarRespuesta(conv.getId());

        assertThat(respuestasDelBot(conv)).isZero();
        assertThat(recargar(conv).getStatus()).isEqualTo(ConversationStatus.HUMANO_CONTROL);
    }

    @Test
    void siElAsesorNoRespondeEnElPlazoElBotRetomaLaConversacion() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        stateService.tomarControl(conv.getId(), UUID.randomUUID());
        Conversation tomada = recargar(conv);
        tomada.setTakenAt(Instant.now().minus(Duration.ofMinutes(31)));
        conversationRepository.saveAndFlush(tomada);
        LLM.respond("¡Hola! Te ayudo mientras tanto.", 50, false, "");

        // El revisor periódico la detecta aunque el cliente no vuelva a escribir.
        new AbandonedConversationSweeper(conversationRepository, messageRepository, stateService, botResponseService).revisar();

        Conversation despues = recargar(conv);
        assertThat(respuestasDelBot(conv)).isEqualTo(1);
        assertThat(despues.getStatus()).isEqualTo(ConversationStatus.ESCALADO_PENDIENTE);
        assertThat(despues.getAssignedSalespersonId()).isNull();
    }

    @Test
    void siElAsesorYaRespondioRecientementeNoSeLeQuitaLaConversacion() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        stateService.tomarControl(conv.getId(), UUID.randomUUID());
        Conversation tomada = recargar(conv);
        tomada.setTakenAt(Instant.now().minus(Duration.ofHours(2)));
        conversationRepository.saveAndFlush(tomada);
        messageRepository.save(Message.builder().conversation(tomada).sender(MessageSender.COMERCIAL)
            .content("Ya te confirmo").createdAt(Instant.now().minus(Duration.ofMinutes(5))).build());
        mensajeCliente(conv, "Gracias");

        new AbandonedConversationSweeper(conversationRepository, messageRepository, stateService, botResponseService).revisar();

        assertThat(respuestasDelBot(conv)).isZero();
        assertThat(recargar(conv).getStatus()).isEqualTo(ConversationStatus.HUMANO_CONTROL);
    }

    @Test
    void anteElLimiteDeUsoDelProveedorReintentaYRespondeNormal() {
        Conversation conv = conversacion(ConversationStatus.BOT_ACTIVO);
        LLM.respond("La placa cuesta $65 USD.", 30, false, "");
        LLM.rateLimitNext(2);

        botResponseService.generarYEncolarRespuesta(conv.getId());

        Conversation despues = recargar(conv);
        assertThat(despues.getStatus()).isEqualTo(ConversationStatus.BOT_ACTIVO);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).stream()
            .filter(m -> m.getSender() == MessageSender.BOT).map(Message::getContent))
            .containsExactly("La placa cuesta $65 USD.");
    }

    // ----------------------------------------------------------------- helpers

    private Conversation conversacion(ConversationStatus status) {
        Customer customer = customerRepository.save(Customer.builder()
            .phoneNumber("+57" + UUID.randomUUID().toString().substring(0, 10)).displayName("Cliente").build());
        Conversation conv = conversationRepository.save(Conversation.builder().company(empresa).customer(customer)
            .status(status).build());
        mensajeCliente(conv, "¿Qué costo tiene?");
        return conv;
    }

    private void mensajeCliente(Conversation conv, String texto) {
        messageRepository.save(Message.builder().conversation(conv).sender(MessageSender.CLIENTE).content(texto).build());
    }

    private long respuestasDelBot(Conversation conv) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).stream()
            .filter(m -> m.getSender() == MessageSender.BOT).count();
    }

    private Conversation recargar(Conversation conv) {
        return conversationRepository.findById(conv.getId()).orElseThrow();
    }
}
