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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks SMS settings from Administration &gt; SMS before they are saved. Returns message keys
 * ({@code sms.config.error.*}) so the page can show them in the user's language.
 *
 * @since 2026-09-24
 */
@Service
public class SmsConfigValidator {
    /** Longest webhook secret that still fits {@code sms_config.webhook_secret} (512) once encrypted. */
    static final int MAX_WEBHOOK_SECRET_LENGTH = 256;

    /**
     * @param update             the submitted settings
     * @param installedProviders providers that have a client; choosing another would make every send fail
     * @return message keys for each problem; empty when the settings can be saved
     */
    public List<String> validate(SmsConfigUpdateDto update, Set<SmsProviderType> installedProviders) {
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
        if (update.webhookSecret() != null && update.webhookSecret().length() > MAX_WEBHOOK_SECRET_LENGTH) {
            errors.add("sms.config.error.webhookSecretTooLong");
        }
        return errors;
    }
}
