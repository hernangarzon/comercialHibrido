package com.comercialhibrido.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppWebhookPayload(
    String object,
    List<Entry> entry
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
        String id,
        List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
        ChangeValue value,
        String field
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangeValue(
        @JsonProperty("messaging_product") String messagingProduct,
        Metadata metadata,
        List<Contact> contacts,
        List<IncomingMessage> messages,
        List<StatusUpdate> statuses
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
        @JsonProperty("display_phone_number") String displayPhoneNumber,
        @JsonProperty("phone_number_id") String phoneNumberId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(
        Profile profile,
        @JsonProperty("wa_id") String waId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
        String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomingMessage(
        String from,
        String id,
        String timestamp,
        String type,
        TextBody text,
        MediaPayload image,
        DocumentPayload document
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextBody(
        String body
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaPayload(
        String id,
        @JsonProperty("mime_type") String mimeType,
        String caption
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentPayload(
        String id,
        String filename,
        @JsonProperty("mime_type") String mimeType,
        String caption
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdate(
        String id,
        String status,
        String timestamp,
        @JsonProperty("recipient_id") String recipientId,
        List<StatusError> errors
    ) {
        public String errorSummary() {
            if (errors == null || errors.isEmpty()) {
                return null;
            }
            return errors.stream()
                .map(e -> String.format("[%s] %s: %s", e.code(), e.title(), e.message()))
                .collect(Collectors.joining("; "));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusError(
        Integer code,
        String title,
        String message
    ) {}
}