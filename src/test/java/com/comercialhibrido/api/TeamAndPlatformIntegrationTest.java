package com.comercialhibrido.api;

import com.comercialhibrido.domain.entity.Company;
import com.comercialhibrido.domain.entity.User;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.SalesLeadRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Equipo (invitaciones, roles, desactivación), cuenta propia, marca, WhatsApp,
 * puesta en marcha y la landing (solicitudes públicas y su bandeja de plataforma).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamAndPlatformIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CompanyRepository companyRepository;
    @Autowired UserRepository userRepository;
    @Autowired SalesLeadRepository salesLeadRepository;
    @Autowired PasswordService passwordService;

    private Company empresa;
    private Company otra;
    private User adminUser;
    private String admin;
    private String comercial;
    private String adminOtra;
    private String sufijo;

    @BeforeEach
    void preparar() throws Exception {
        sufijo = UUID.randomUUID().toString().substring(0, 8);
        empresa = companyRepository.save(Company.builder().name("Empresa").whatsappPhoneNumberId("1" + sufijo.hashCode()).build());
        otra = companyRepository.save(Company.builder().name("Otra").whatsappPhoneNumberId("9" + Math.abs(sufijo.hashCode())).build());
        adminUser = crearUsuario(empresa, "admin-" + sufijo + "@test.com", "ADMIN");
        admin = token(adminUser.getEmail(), PASSWORD);
        comercial = token(crearUsuario(empresa, "com-" + sufijo + "@test.com", "COMERCIAL").getEmail(), PASSWORD);
        adminOtra = token(crearUsuario(otra, "otra-" + sufijo + "@test.com", "ADMIN").getEmail(), PASSWORD);
    }

    @Test
    void invitacionConContrasenaTemporalObligaACambiarlaAlEntrar() throws Exception {
        String email = "nuevo-" + sufijo + "@test.com";
        mvc.perform(post("/api/team").header("Authorization", comercial).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Nuevo", "email", email, "role", "COMERCIAL"))))
            .andExpect(status().isForbidden());

        JsonNode invitacion = body(mvc.perform(post("/api/team").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Nuevo", "email", email.toUpperCase(), "role", "comercial"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member.mustChangePassword").value(true))
            .andExpect(jsonPath("$.member.role").value("COMERCIAL")));
        String temporal = invitacion.get("temporaryPassword").asText();
        assertThat(temporal).hasSize(14);

        mvc.perform(post("/api/team").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Otro", "email", email, "role", "COMERCIAL"))))
            .andExpect(status().isConflict());

        JsonNode login = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", temporal))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true)));
        String nuevo = "Bearer " + login.get("token").asText();

        mvc.perform(get("/api/conversations").header("Authorization", nuevo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/account/password").header("Authorization", nuevo).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", "mala", "newPassword", "unaClaveNueva1"))))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/password").header("Authorization", nuevo).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", temporal, "newPassword", "corta"))))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/password").header("Authorization", nuevo).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", temporal, "newPassword", "unaClaveNueva1"))))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/conversations").header("Authorization", nuevo)).andExpect(status().isOk());
    }

    @Test
    void cambiosDeRolYDesactivacionAplicanDeInmediatoSinVolverAEntrar() throws Exception {
        User vendedor = crearUsuario(empresa, "vend-" + sufijo + "@test.com", "COMERCIAL");
        String tokenVendedor = token(vendedor.getEmail(), PASSWORD);

        String knowledge = json(Map.of("knowledgeBase", "x", "customPrompt", ""));
        mvc.perform(put("/api/company/knowledge").header("Authorization", tokenVendedor)
                .contentType(MediaType.APPLICATION_JSON).content(knowledge))
            .andExpect(status().isForbidden());

        actualizar(admin, vendedor.getId(), "ADMIN", true).andExpect(status().isOk());
        mvc.perform(put("/api/company/knowledge").header("Authorization", tokenVendedor)
                .contentType(MediaType.APPLICATION_JSON).content(knowledge))
            .andExpect(status().isOk());

        actualizar(admin, vendedor.getId(), "COMERCIAL", false).andExpect(status().isOk());
        mvc.perform(get("/api/conversations").header("Authorization", tokenVendedor)).andExpect(status().isUnauthorized());

        // Otra empresa no puede tocar a este usuario.
        actualizar(adminOtra, vendedor.getId(), "ADMIN", true).andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeQuedarSinAdministradorNiAutodegradarse() throws Exception {
        actualizar(admin, adminUser.getId(), "COMERCIAL", true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("No puedes quitarte el rol de administrador ni desactivar tu propia cuenta."));
        actualizar(admin, adminUser.getId(), "ADMIN", false).andExpect(status().isBadRequest());

        mvc.perform(post("/api/team/" + adminUser.getId() + "/reset-password").header("Authorization", admin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void restablecerContrasenaInvalidaLaAnteriorYObligaACambiarla() throws Exception {
        User vendedor = crearUsuario(empresa, "reset-" + sufijo + "@test.com", "COMERCIAL");
        String temporal = body(mvc.perform(post("/api/team/" + vendedor.getId() + "/reset-password").header("Authorization", admin))
            .andExpect(status().isOk())).get("temporaryPassword").asText();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", vendedor.getEmail(), "password", PASSWORD))))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", vendedor.getEmail(), "password", temporal))))
            .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void marcaValidaContrasteYFormatoDelLogo() throws Exception {
        mvc.perform(put("/api/company/branding").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("brandColor", "#ffe066", "logoDataUrl", ""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Ese color es muy claro")));
        mvc.perform(put("/api/company/branding").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("brandColor", "#0f766e", "logoDataUrl", "data:image/svg+xml;base64,PHN2Zz4="))))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/company/branding").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("brandColor", "#0F766E", "logoDataUrl", "data:image/png;base64,iVBORw0KGgo="))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandColor").value("#0f766e"));
        mvc.perform(get("/api/company").header("Authorization", comercial))
            .andExpect(jsonPath("$.logoDataUrl").value("data:image/png;base64,iVBORw0KGgo="));
    }

    @Test
    void whatsappSeVerificaAntesDeGuardarYNoSeComparteEntreEmpresas() throws Exception {
        mvc.perform(put("/api/company/whatsapp").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phoneNumberId", otra.getWhatsappPhoneNumberId(), "accessToken", "x"))))
            .andExpect(status().isConflict());
        // En el perfil test Meta apunta a localhost:1: la verificación falla y no se guarda nada.
        mvc.perform(put("/api/company/whatsapp").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phoneNumberId", "123456789012", "accessToken", "token"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("No se pudo contactar a Meta")));
        assertThat(companyRepository.findById(empresa.getId()).orElseThrow().getWhatsappPhoneNumberId())
            .isEqualTo(empresa.getWhatsappPhoneNumberId());
        mvc.perform(put("/api/company/whatsapp").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phoneNumberId", "abc"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void puestaEnMarchaListaLosPasosYOcultaElWebhookALasEmpresas() throws Exception {
        JsonNode onboarding = body(mvc.perform(get("/api/company/onboarding").header("Authorization", admin)).andExpect(status().isOk()));
        assertThat(onboarding.get("steps")).hasSize(6);
        assertThat(onboarding.get("steps").get(0).get("done").asBoolean()).isTrue();
        assertThat(onboarding.get("steps").get(4).get("done").asBoolean()).isTrue(); // 2 usuarios activos
        assertThat(onboarding.get("platformWebhook").isNull()).isTrue();
    }

    @Test
    void landingRecibeSolicitudesConAntispamYSoloLaPlataformaLasVe() throws Exception {
        String ip = "10.1." + Math.abs(sufijo.hashCode() % 250) + ".7";
        Map<String, Object> lead = new HashMap<>(Map.of("name", "Ana", "companyName", "Clínica " + sufijo,
            "email", "ana@clinica.com", "phone", "+57 300 123 4567", "message", "Quiero una demo", "acceptedPrivacy", true));

        Map<String, Object> sinAutorizacion = new HashMap<>(lead);
        sinAutorizacion.put("acceptedPrivacy", false);
        mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(sinAutorizacion)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Debes autorizar el tratamiento de tus datos para enviar la solicitud"));

        mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(lead)))
            .andExpect(status().isAccepted());

        Map<String, Object> bot = new HashMap<>(lead);
        bot.put("companyName", "Spam " + sufijo);
        bot.put("website", "http://spam");
        mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(bot)))
            .andExpect(status().isAccepted());
        assertThat(salesLeadRepository.findAll()).noneMatch(l -> l.getCompanyName().equals("Spam " + sufijo));

        Map<String, Object> invalida = new HashMap<>(lead);
        invalida.put("email", "no-es-correo");
        mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(invalida)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("El correo no es válido"));

        for (int i = 0; i < 4; i++) {
            mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(lead)));
        }
        mvc.perform(desde(ip, post("/api/public/leads")).contentType(MediaType.APPLICATION_JSON).content(json(lead)))
            .andExpect(status().isTooManyRequests());

        mvc.perform(get("/api/public/config")).andExpect(jsonPath("$.salesWhatsapp").value("573001112222"));

        mvc.perform(get("/api/platform/leads").header("Authorization", admin)).andExpect(status().isNotFound());
        String plataforma = token(crearUsuario(empresa, "platform@test.com", "ADMIN").getEmail(), PASSWORD);
        JsonNode leads = body(mvc.perform(get("/api/platform/leads").header("Authorization", plataforma)).andExpect(status().isOk()));
        JsonNode recibida = null;
        for (JsonNode l : leads) if (l.get("companyName").asText().equals("Clínica " + sufijo)) recibida = l;
        assertThat(recibida).isNotNull();
        assertThat(recibida.get("status").asText()).isEqualTo("NUEVA");

        mvc.perform(put("/api/platform/leads/" + recibida.get("id").asText()).header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CONTACTADA"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONTACTADA"));
    }

    // ----------------------------------------------------------------- helpers

    private User crearUsuario(Company company, String email, String role) {
        return userRepository.save(User.builder().company(company).email(email).name("Usuario " + role)
            .role(role).passwordHash(passwordService.hashPassword(PASSWORD)).build());
    }

    private String token(String email, String password) throws Exception {
        return "Bearer " + body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", email, "password", password)))).andExpect(status().isOk())).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions actualizar(String auth, UUID id, String role, boolean active) throws Exception {
        return mvc.perform(put("/api/team/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("role", role, "active", active))));
    }

    private static MockHttpServletRequestBuilder desde(String ip, MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
