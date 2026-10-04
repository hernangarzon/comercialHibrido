package com.comercialhibrido.api;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.Conversation;
import com.comercialhibrido.domain.entity.Customer;
import com.comercialhibrido.domain.entity.Message;
import com.comercialhibrido.domain.entity.OutboundMessageJob;
import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.domain.enums.ConversationStatus;
import com.comercialhibrido.domain.enums.DeliveryStatus;
import com.comercialhibrido.domain.enums.JobStatus;
import com.comercialhibrido.domain.enums.MessageSender;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ConversationRepository;
import com.comercialhibrido.repository.CustomerRepository;
import com.comercialhibrido.repository.InboundMessageJobRepository;
import com.comercialhibrido.repository.MessageRepository;
import com.comercialhibrido.repository.OutboundMessageJobRepository;
import com.comercialhibrido.repository.UserRepository;
import com.comercialhibrido.security.PasswordService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el API real (filtros, controllers, JPA sobre H2) en los puntos de seguridad:
 * login, aislamiento multiempresa, firma del webhook, idempotencia y estados de entrega.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIntegrationTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String PASSWORD = "clave-de-prueba";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired InboundMessageJobRepository inboundMessageJobRepository;
    @Autowired OutboundMessageJobRepository outboundMessageJobRepository;
    @Autowired PasswordService passwordService;

    private Company empresaA;
    private Company empresaB;
    private String emailA;
    private String emailB;
    private Conversation conversacionB;

    @BeforeEach
    void preparar() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        empresaA = companyRepository.save(Company.builder().name("A").whatsappPhoneNumberId("a-" + sufijo).build());
        empresaB = companyRepository.save(Company.builder().name("B").whatsappPhoneNumberId("b-" + sufijo).build());
        emailA = "a-" + sufijo + "@test.com";
        emailB = "b-" + sufijo + "@test.com";
        crearUsuario(empresaA, emailA, passwordService.hashPassword(PASSWORD));
        crearUsuario(empresaB, emailB, passwordService.hashPassword(PASSWORD));
        conversacionB = crearConversacion(empresaB, "+57-b-" + sufijo, ConversationStatus.ESCALADO_PENDIENTE);
    }

    @Test
    void loginDevuelveElMismoErrorParaEmailInexistenteYContrasenaErronea() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "nadie@test.com", "password", PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Correo o contraseña incorrectos."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", emailA, "password", "mala")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Correo o contraseña incorrectos."));
    }

    @Test
    void loginConContrasenaEnTextoPlanoLaMigraABcrypt() throws Exception {
        String email = "plano-" + UUID.randomUUID() + "@test.com";
        User user = crearUsuario(empresaA, email, "admin123");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", "admin123")))
            .andExpect(status().isOk());

        String guardado = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(guardado).startsWith("$2").isNotEqualTo("admin123");
    }

    @Test
    void apiRequiereToken() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/media/cualquiera")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaEmpresaNoVeNiOperaConversacionesDeOtra() throws Exception {
        String tokenA = login(emailA);
        String tokenB = login(emailB);
        UUID id = conversacionB.getId();

        // Listado: A no ve la conversación de B, ni aunque pase companyId de B por query.
        String listaA = mvc.perform(get("/api/conversations")
                .param("companyId", empresaB.getId().toString())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listaA).doesNotContain(id.toString());

        mvc.perform(get("/api/conversations/" + id + "/mensajes").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/conversations/" + id + "/tomar-control").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/conversations/" + id + "/archivar").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/conversations/" + id + "/mensajes").header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON).content(json("content", "hola")))
            .andExpect(status().isNotFound());

        // B sí puede, y queda asignado el usuario del token.
        mvc.perform(post("/api/conversations/" + id + "/tomar-control").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk());
        Conversation actualizada = conversationRepository.findById(id).orElseThrow();
        assertThat(actualizada.getStatus()).isEqualTo(ConversationStatus.HUMANO_CONTROL);
        assertThat(actualizada.getAssignedSalespersonId())
            .isEqualTo(userRepository.findByEmailAndActiveTrue(emailB).orElseThrow().getId());
    }

    @Test
    void archivoMultimediaDeOtraEmpresaDevuelve404() throws Exception {
        messageRepository.save(Message.builder().conversation(conversacionB).sender(MessageSender.CLIENTE)
            .content("[imagen]").mediaType("IMAGE").mediaId("media-de-b").build());

        mvc.perform(get("/api/media/media-de-b").header("Authorization", "Bearer " + login(emailA)))
            .andExpect(status().isNotFound());
    }

    @Test
    void webhookRechazaFirmaAusenteOInvalida() throws Exception {
        byte[] body = mensajeEntrante(empresaA.getWhatsappPhoneNumberId(), "wamid." + UUID.randomUUID());

        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)))
            .andExpect(status().isForbidden());
    }

    @Test
    void webhookFirmadoGuardaElMensajeUnaSolaVezYEnLaEmpresaCorrecta() throws Exception {
        String wamid = "wamid." + UUID.randomUUID();
        byte[] body = mensajeEntrante(empresaA.getWhatsappPhoneNumberId(), wamid);

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("X-Hub-Signature-256", firmar(body)))
                .andExpect(status().isOk());
        }

        Message guardado = messageRepository.findByWhatsappMessageId(wamid).orElseThrow();
        Conversation conv = conversationRepository.findById(guardado.getConversation().getId()).orElseThrow();
        assertThat(conv.getCompany().getId()).isEqualTo(empresaA.getId());
        assertThat(inboundMessageJobRepository.existsByWhatsappMessageId(wamid)).isTrue();
        assertThat(inboundMessageJobRepository.findAll().stream()
            .filter(j -> j.getWhatsappMessageId().equals(wamid))).hasSize(1);
    }

    @Test
    void webhookDeEstadoActualizaLaEntregaDelMensaje() throws Exception {
        Message saliente = messageRepository.save(Message.builder().conversation(conversacionB)
            .sender(MessageSender.BOT).content("hola").whatsappMessageId("wamid.out-" + UUID.randomUUID()).build());
        outboundMessageJobRepository.save(OutboundMessageJob.builder()
            .messageId(saliente.getId()).conversationId(conversacionB.getId()).toPhoneNumber("000").body("hola")
            .status(JobStatus.COMPLETED).whatsappMessageId(saliente.getWhatsappMessageId()).build());

        byte[] body = ("""
            {"entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"%s"},
              "statuses":[{"id":"%s","status":"read","recipient_id":"000"}]}}]}]}
            """.formatted(empresaB.getWhatsappPhoneNumberId(), saliente.getWhatsappMessageId()))
            .getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhook/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Hub-Signature-256", firmar(body)))
            .andExpect(status().isOk());

        assertThat(messageRepository.findById(saliente.getId()).orElseThrow().getDeliveryStatus())
            .isEqualTo(DeliveryStatus.READ);
    }

    @Test
    void unJobSoloPuedeReclamarseUnaVez() {
        OutboundMessageJob job = outboundMessageJobRepository.save(OutboundMessageJob.builder()
            .messageId(UUID.randomUUID()).conversationId(conversacionB.getId())
            .toPhoneNumber("000").body("x").build());

        assertThat(reclamar(job.getId())).isEqualTo(1);
        assertThat(reclamar(job.getId())).isZero();
        assertThat(outboundMessageJobRepository.findById(job.getId()).orElseThrow().getStatus())
            .isEqualTo(JobStatus.PROCESSING);
    }

    // ----------------------------------------------------------------- helpers

    @Autowired org.springframework.transaction.support.TransactionTemplate tx;

    private int reclamar(UUID id) {
        return tx.execute(s -> outboundMessageJobRepository.claim(id, Instant.now()));
    }

    private User crearUsuario(Company company, String email, String passwordHash) {
        return userRepository.save(User.builder().company(company).email(email).name("Usuario")
            .passwordHash(passwordHash).build());
    }

    private Conversation crearConversacion(Company company, String phone, ConversationStatus status) {
        Customer customer = customerRepository.save(Customer.builder().phoneNumber(phone).displayName("Cliente").build());
        return conversationRepository.save(Conversation.builder().company(company).customer(customer).status(status).build());
    }

    private String login(String email) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode nodo = objectMapper.readTree(respuesta);
        return nodo.get("token").asText();
    }

    private byte[] mensajeEntrante(String phoneNumberId, String wamid) {
        String from = "57" + Math.abs(wamid.hashCode());
        return ("""
            {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"field":"messages","value":{
              "metadata":{"phone_number_id":"%s"},
              "contacts":[{"wa_id":"%s","profile":{"name":"Cliente Test"}}],
              "messages":[{"from":"%s","id":"%s","timestamp":"1","type":"text","text":{"body":"Hola"}}]}}]}]}
            """.formatted(phoneNumberId, from, from, wamid)).getBytes(StandardCharsets.UTF_8);
    }

    private static String firmar(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private String json(String... claveValor) throws Exception {
        java.util.Map<String, String> mapa = new java.util.LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put(claveValor[i], claveValor[i + 1]);
        }
        return objectMapper.writeValueAsString(mapa);
    }
}
