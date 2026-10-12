package com.comercialhibrido.config;

import com.comercialhibrido.security.TokenCipher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Al arrancar con AGENT_ENCRYPTION_KEY, cifra los tokens de WhatsApp que quedaron en
 * texto plano (guardados antes de activar el cifrado). Es idempotente: los ya cifrados
 * se saltan.
 */
@Component
@RequiredArgsConstructor
public class EncryptExistingTokensRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EncryptExistingTokensRunner.class);

    private final TokenCipher cipher;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!cipher.isEnabled()) {
            return;
        }
        List<Map<String, Object>> plain = jdbc.queryForList(
            "SELECT id, whatsapp_access_token FROM companies "
                + "WHERE whatsapp_access_token IS NOT NULL AND whatsapp_access_token NOT LIKE ?",
            TokenCipher.PREFIX + "%");
        for (Map<String, Object> row : plain) {
            jdbc.update("UPDATE companies SET whatsapp_access_token = ? WHERE id = ?",
                cipher.encrypt((String) row.get("whatsapp_access_token")), row.get("id"));
        }
        if (!plain.isEmpty()) {
            log.info("Se cifraron {} token(s) de WhatsApp guardados en texto plano.", plain.size());
        }
    }
}
