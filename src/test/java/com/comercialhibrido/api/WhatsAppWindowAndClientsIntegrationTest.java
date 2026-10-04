package com.comercialhibrido.api;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Customer;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.SalesLead;
import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.CustomerRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.SalesLeadRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.PasswordService;
import com.comercialhibrido.support.FakeMetaServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ventana de 24 h y plantillas de WhatsApp contra una Meta simulada (con el worker
 * enviando de verdad), y alta y suspensión de clientes desde la plataforma.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsAppWindowAndClientsIntegrationTest {

    private static final FakeMetaServer META = new FakeMetaServer();
    private static final String PASSWORD = "clave-de-prueba";
    private static final String PLATFORM_EMAIL = "plataforma-ventana@test.com";

    @DynamicPropertySource
    static void meta(DynamicPropertyRegistry registry) {
        // Base propia: este contexto tiene el worker activo y no debe tocar datos de otros tests.
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:ventana;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        registry.add("platform.admin-emails", () -> PLATFORM_EMAIL);
        registry.add("whatsapp.api-base-url", META::baseUrl);
        registry.add("whatsapp.access-token", () -> FakeMetaServer.TOKEN);
        registry.add("agent.worker-enabled", () -> "true");
        registry.add("agent.worker-fixed-delay-ms", () -> "250");
    }

    @AfterAll
    static void stop() {
        META.close();
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired SalesLeadRepository salesLeadRepository;
    @Autowired PasswordService passwordService;

    private Company empresa;
    private String admin;
    private String sufijo;

    @BeforeEach
    void preparar() throws Exception {
        sufijo = UUID.randomUUID().toString().substring(0, 8);
        empresa = companyRepository.save(Company.builder().name("Empresa " + sufijo).build());
        admin = token(crearUsuario(empresa, "admin-" + sufijo + "@test.com"));
    }

    @Test
    void conectarWhatsappVerificaNumeroYCuentaContraMeta() throws Exception {
        conectar(FakeMetaServer.PHONE_ID + "0", "").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Ese Phone Number ID no existe o el token no tiene acceso a él."));
        conectar(FakeMetaServer.PHONE_ID, "999999999").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Meta no reconoce ese ID de cuenta")));

        conectar(FakeMetaServer.PHONE_ID, FakeMetaServer.WABA_ID).andExpect(status().isOk())
            .andExpect(jsonPath("$.verification.verifiedName").value("Empresa de Prueba"));

        JsonNode plantillas = body(mvc.perform(get("/api/company/whatsapp/templates").header("Authorization", admin)).andExpect(status().isOk()));
        assertThat(plantillas).hasSize(2);
        assertThat(plantillas.get(0).get("name").asText()).isEqualTo("seguimiento");
        assertThat(plantillas.get(0).get("paramCount").asInt()).isEqualTo(2);
        assertThat(plantillas.get(1).get("paramCount").asInt()).isZero();
        assertThat(plantillas.toString()).doesNotContain("rechazada");

        // Libera el número para las demás pruebas (es único por empresa).
        empresa = companyRepository.findById(empresa.getId()).orElseThrow();
        empresa.setWhatsappPhoneNumberId(null);
        companyRepository.save(empresa);
    }

    @Test
    void conVentanaCerradaSoloSePuedeEnviarUnaPlantillaYElWorkerLaEntregaAMeta() throws Exception {
        empresa.setWhatsappBusinessAccountId(FakeMetaServer.WABA_ID);
        companyRepository.save(empresa);
        Conversation conv = conversacion(Instant.now().minus(Duration.ofHours(30)));

        JsonNode lista = body(mvc.perform(get("/api/conversations").header("Authorization", admin)));
        assertThat(Instant.parse(lista.get(0).get("windowClosesAt").asText())).isBefore(Instant.now());

        mvc.perform(post("/api/conversations/" + conv.getId() + "/mensajes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "¿Sigues interesado?"))))
            .andExpect(status().isConflict());

        plantilla(conv, "seguimiento", List.of("Ana")).andExpect(status().isBadRequest());
        plantilla(conv, "rechazada", List.of()).andExpect(status().isBadRequest());
        int antes = META.sentPayloads().size();
        plantilla(conv, "seguimiento", List.of("Ana", "coronas")).andExpect(status().isAccepted());

        Message enviado = esperarEnvio(conv.getId());
        assertThat(enviado.getContent()).isEqualTo("Hola Ana, ¿seguimos con tu pedido de coronas?");
        assertThat(enviado.getMediaType()).isEqualTo("TEMPLATE");
        assertThat(enviado.getWhatsappMessageId()).startsWith("wamid.fake-");

        JsonNode payload = objectMapper.readTree(META.sentPayloads().get(antes));
        assertThat(payload.get("type").asText()).isEqualTo("template");
        assertThat(payload.at("/template/name").asText()).isEqualTo("seguimiento");
        assertThat(payload.at("/template/language/code").asText()).isEqualTo("es");
        assertThat(payload.at("/template/components/0/parameters/1/text").asText()).isEqualTo("coronas");
    }

    @Test
    void conVentanaAbiertaSePuedeEscribirTextoLibre() throws Exception {
        Conversation conv = conversacion(Instant.now().minus(Duration.ofHours(2)));
        mvc.perform(post("/api/conversations/" + conv.getId() + "/mensajes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "Hola, te ayudo"))))
            .andExpect(status().isAccepted());
        Message enviado = esperarEnvio(conv.getId());
        assertThat(enviado.getMediaType()).isEqualTo("TEXT");
    }

    @Test
    void laPlataformaConvierteUnaSolicitudEnClienteYPuedeSuspenderlo() throws Exception {
        User dueno = userRepository.findByEmailAndActiveTrue(PLATFORM_EMAIL).orElseGet(() -> crearUsuario(empresa, PLATFORM_EMAIL));
        String plataforma = token(dueno);
        Company empresaDueno = dueno.getCompany();
        SalesLead lead = salesLeadRepository.save(SalesLead.builder().name("Rosa").companyName("Óptica Rosa " + sufijo)
            .email("rosa-" + sufijo + "@optica.com").phone("+57 300 999 8888").build());

        String nuevo = json(Map.of("companyName", lead.getCompanyName(), "adminName", "Rosa", "adminEmail", lead.getEmail(),
            "leadId", lead.getId().toString()));
        mvc.perform(post("/api/platform/companies").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(nuevo))
            .andExpect(status().isNotFound());

        JsonNode creado = body(mvc.perform(post("/api/platform/companies").header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(nuevo))
            .andExpect(status().isOk()));
        String companyId = creado.at("/company/id").asText();
        String temporal = creado.at("/invitation/temporaryPassword").asText();
        assertThat(creado.at("/company/whatsappConnected").asBoolean()).isFalse();
        assertThat(salesLeadRepository.findById(lead.getId()).orElseThrow().getStatus()).isEqualTo(SalesLead.CONVERTIDA);

        mvc.perform(post("/api/platform/companies").header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(nuevo))
            .andExpect(status().isConflict());

        // El admin del cliente entra con la temporal, la cambia y usa el panel.
        String rosa = "Bearer " + body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", lead.getEmail(), "password", temporal))))
            .andExpect(jsonPath("$.mustChangePassword").value(true))).get("token").asText();
        mvc.perform(put("/api/account/password").header("Authorization", rosa).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", temporal, "newPassword", "RosaSegura123"))))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/company/onboarding").header("Authorization", rosa))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps[0].done").value(false));

        // Suspendida: queda fuera del panel al instante y no puede volver a entrar.
        mvc.perform(put("/api/platform/companies/" + companyId).header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("active", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/conversations").header("Authorization", rosa)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", lead.getEmail(), "password", "RosaSegura123"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("La cuenta de tu empresa está suspendida. Contacta a soporte."));

        mvc.perform(put("/api/platform/companies/" + empresaDueno.getId()).header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("active", false))))
            .andExpect(status().isBadRequest());

        JsonNode clientes = body(mvc.perform(get("/api/platform/companies").header("Authorization", plataforma)));
        assertThat(clientes.toString()).contains(lead.getCompanyName());
    }

    // ----------------------------------------------------------------- helpers

    private Conversation conversacion(Instant ultimoMensajeCliente) {
        Customer customer = customerRepository.save(Customer.builder().phoneNumber("+57" + sufijo.hashCode()).displayName("Cliente").build());
        Conversation conv = conversationRepository.save(Conversation.builder().company(empresa).customer(customer)
            .status(ConversationStatus.HUMANO_CONTROL).build());
        messageRepository.save(Message.builder().conversation(conv).sender(MessageSender.CLIENTE).content("Hola")
            .createdAt(ultimoMensajeCliente).build());
        return conv;
    }

    private Message esperarEnvio(UUID conversationId) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            Message m = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(x -> x.getSender() == MessageSender.COMERCIAL && x.getDeliveryStatus() == DeliveryStatus.SENT)
                .findFirst().orElse(null);
            if (m != null) return m;
            Thread.sleep(200);
        }
        throw new AssertionError("El worker no envió el mensaje a Meta");
    }

    private ResultActions conectar(String phoneId, String waba) throws Exception {
        return mvc.perform(put("/api/company/whatsapp").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("phoneNumberId", phoneId, "accessToken", FakeMetaServer.TOKEN, "businessAccountId", waba))));
    }

    private ResultActions plantilla(Conversation conv, String name, List<String> params) throws Exception {
        return mvc.perform(post("/api/conversations/" + conv.getId() + "/plantilla").header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name, "language", "es", "params", params))));
    }

    private User crearUsuario(Company company, String email) {
        return userRepository.save(User.builder().company(company).email(email).name("Admin")
            .role("ADMIN").passwordHash(passwordService.hashPassword(PASSWORD)).build());
    }

    private String token(User user) throws Exception {
        return "Bearer " + body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", user.getEmail(), "password", PASSWORD)))).andExpect(status().isOk())).get("token").asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
