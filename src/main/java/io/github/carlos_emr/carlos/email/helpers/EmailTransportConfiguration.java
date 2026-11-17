/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.email.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.utility.EmailSendingException;

import java.io.IOException;

/** Parses stored transport settings without exposing credentials in parse errors. */
final class EmailTransportConfiguration {
    private EmailTransportConfiguration() { }

    static JsonNode parse(EmailConfig configuration) throws EmailSendingException {
        String json = configuration == null ? null : configuration.getConfigDetailsJson();
        if (json == null || json.isBlank()) {
            throw new EmailSendingException("No email transport configuration is stored");
        }
        try {
            JsonNode settings = new ObjectMapper().readTree(json);
            if (settings != null && settings.isObject()) { return settings; }
        } catch (IOException invalidJson) {
            // Jackson's exception may quote the configuration, including passwords/API keys.
            throw new EmailSendingException("Invalid email transport configuration JSON");
        }
        throw new EmailSendingException("Email transport configuration must be a JSON object");
    }

    static String requiredText(JsonNode settings, String key) throws EmailSendingException {
        JsonNode value = settings.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new EmailSendingException("Missing or invalid email transport setting: " + key);
        }
        return value.textValue();
    }

    static int port(JsonNode settings) throws EmailSendingException {
        JsonNode value = settings.get("port");
        if (value != null && (value.isTextual() || value.isIntegralNumber())) {
            try {
                int port = Integer.parseInt(value.asText());
                if (port >= 1 && port <= 65535) { return port; }
            } catch (NumberFormatException invalidPort) {
                // Report the field, never the stored configuration or parser exception.
            }
        }
        throw new EmailSendingException("Email transport port must be between 1 and 65535");
    }
}
