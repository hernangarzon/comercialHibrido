package com.comercialhibrido.domain.entity;

import com.comercialhibrido.security.TokenCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Cifra el valor al guardarlo y lo descifra al leerlo: el resto del código trabaja
 * con el token en claro sin saber que en la base está cifrado.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final TokenCipher cipher;

    public EncryptedStringConverter(TokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
