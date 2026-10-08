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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What an SMS provider gets from the clinic's saved settings (Administration &gt; SMS) when it sends or looks up
 * a text: the sender number and the provider's own logins.
 * <p>
 * Only the clinic's active provider gets them; any other provider gets {@link #none(SmsProviderType)}, so a
 * provider can never be handed another provider's logins. A provider that needs a value it does not get must
 * answer with a definite failure, never send. {@link #toString()} never shows a value.
 *
 * @since 2026-10-08
 */
public final class SmsProviderSettings {
    private final SmsProviderType providerType;
    private final String senderNumber;
    private final Map<String, String> credentials;

    private SmsProviderSettings(SmsProviderType providerType, String senderNumber, Map<String, String> credentials) {
        this.providerType = Objects.requireNonNull(providerType, "SMS provider type is required");
        this.senderNumber = senderNumber == null || senderNumber.isBlank() ? null : senderNumber;
        this.credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    /**
     * @param providerType the provider the settings belong to
     * @param senderNumber the sender number in E.164 form (+14165550123), or {@code null} for none
     * @param credentials  the provider's logins by field name, decrypted; blank values are left out
     * @return the settings
     */
    public static SmsProviderSettings of(SmsProviderType providerType, String senderNumber,
                                         Map<String, String> credentials) {
        Map<String, String> present = new HashMap<>();
        if (credentials != null) {
            credentials.forEach((name, value) -> {
                if (name != null && value != null && !value.isBlank()) {
                    present.put(name, value);
                }
            });
        }
        return new SmsProviderSettings(providerType, senderNumber, present);
    }

    /** @return settings with no sender number and no logins, for a provider that is not the active one */
    public static SmsProviderSettings none(SmsProviderType providerType) {
        return new SmsProviderSettings(providerType, null, Map.of());
    }

    public SmsProviderType providerType() {
        return providerType;
    }

    /** @return the sender number in E.164 form, or empty when none is saved */
    public Optional<String> senderNumber() {
        return Optional.ofNullable(senderNumber);
    }

    /** @return the saved value of a login field, or empty when it has none */
    public Optional<String> credential(String name) {
        return Optional.ofNullable(credentials.get(name));
    }

    @Override
    public String toString() {
        return "SmsProviderSettings[" + providerType + ", redacted]";
    }
}
