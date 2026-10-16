/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks SMS settings from Administration &gt; SMS before they are saved. Returns message keys
 * ({@code sms.config.error.*}) so the page can show them in the user's language.
 *
 * @since 2026-09-24
 */
@Service
public class SmsConfigValidator {
    /** Longest webhook secret, in UTF-8 bytes, that still fits {@code sms_config.webhook_secret} (512) once encrypted. */
    static final int MAX_WEBHOOK_SECRET_LENGTH = 256;
    /**
     * Longest single credential, in UTF-8 bytes. All credentials share one {@code TEXT} column (65,535 bytes)
     * after encryption, so this leaves room for a provider that declares many fields.
     */
    static final int MAX_CREDENTIAL_LENGTH = 1024;

    /**
     * What the chosen provider needs before sending can be switched on.
     *
     * @param requiredCredentials  names of the credential fields that must have a value
     * @param senderNumberRequired whether a sender number must be saved
     * @param storedCredentials    names of the credentials already stored for this provider, which a blank
     *                             field keeps
     */
    public record ProviderNeeds(Set<String> requiredCredentials, boolean senderNumberRequired,
                                Set<String> storedCredentials) {
        /** A provider that needs nothing, such as the stub. */
        public static final ProviderNeeds NONE = new ProviderNeeds(Set.of(), false, Set.of());

        public ProviderNeeds {
            requiredCredentials = requiredCredentials == null ? Set.of() : Set.copyOf(requiredCredentials);
            storedCredentials = storedCredentials == null ? Set.of() : Set.copyOf(storedCredentials);
        }
    }

    /**
     * @param update             the submitted settings
     * @param installedProviders providers that have a client; choosing another would make every send fail
     * @param needs              what the chosen provider needs; checked only when sending is switched on, so
     *                           a clinic can choose a provider and enter its credentials before going live
     * @return message keys for each problem; empty when the settings can be saved
     */
    public List<String> validate(SmsConfigUpdateDto update, Set<SmsProviderType> installedProviders,
                                 ProviderNeeds needs) {
        Objects.requireNonNull(needs, "provider needs are required; use ProviderNeeds.NONE for none");
        List<String> errors = new ArrayList<>();
        if (update.providerType() == null) {
            errors.add("sms.config.error.providerRequired");
        } else if (!installedProviders.contains(update.providerType())) {
            errors.add("sms.config.error.providerNotInstalled");
        }
        String senderNumber = update.senderNumber();
        if (senderNumber != null && !senderNumber.isBlank()
                && SmsPhoneNumbers.normalizeToE164(senderNumber).isEmpty()) {
            errors.add("sms.config.error.senderNumber");
        }
        if (update.webhookSecret() != null
                && update.webhookSecret().getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_SECRET_LENGTH) {
            errors.add("sms.config.error.webhookSecretTooLong");
        }
        boolean credentialTooLong = update.credentials() != null && update.credentials().values().stream()
                .anyMatch(value -> value != null
                        && value.getBytes(StandardCharsets.UTF_8).length > MAX_CREDENTIAL_LENGTH);
        if (credentialTooLong) {
            errors.add("sms.config.error.credentialTooLong");
        }
        if (update.enabled()) {
            if (needs.senderNumberRequired() && (senderNumber == null || senderNumber.isBlank())) {
                errors.add("sms.config.error.senderNumberRequired");
            }
            boolean credentialMissing = needs.requiredCredentials().stream()
                    .anyMatch(field -> !needs.storedCredentials().contains(field)
                            && isBlank(update.credentials().get(field)));
            if (credentialMissing) {
                errors.add("sms.config.error.credentialRequired");
            }
        }
        if (update.clearWebhookSecret() && update.webhookSecret() != null && !update.webhookSecret().isBlank()) {
            // Saving would drop the secret just typed: clearing wins, which is not what either choice meant.
            errors.add("sms.config.error.clearAndNewSecret");
        }
        return errors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
