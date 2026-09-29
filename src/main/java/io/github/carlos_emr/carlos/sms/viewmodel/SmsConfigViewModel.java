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
package io.github.carlos_emr.carlos.sms.viewmodel;

import java.util.List;

/**
 * SMS settings for {@code admin/configureSms.jsp}. Secrets appear only as "set" flags; their values
 * never reach the page.
 *
 * @param providerType      selected provider name
 * @param providerOptions   providers with an installed client, the only ones that can be chosen
 * @param enabled           whether sending is on
 * @param schedulerEnabled  the saved (or property) scheduler setting
 * @param schedulerRunning  whether the scheduler is running in this server right now
 * @param senderNumber      the E.164 sender number, or empty
 * @param webhookSecretSet  whether a webhook secret is stored
 * @param credentialFields  the selected provider's credential fields and whether each is stored
 * @param stored            whether settings have been saved; false means the properties still apply
 * @param systemTestEnabled whether {@code sms.systemTest.enabled} lets the system test send
 * @param resultKey         message key for the last action's result, or empty
 * @param errorKeys         message keys for validation errors
 * @since 2026-09-24
 */
public record SmsConfigViewModel(String providerType, List<String> providerOptions, boolean enabled,
                                 boolean schedulerEnabled, boolean schedulerRunning, String senderNumber,
                                 boolean webhookSecretSet, List<CredentialField> credentialFields, boolean stored,
                                 boolean systemTestEnabled, String resultKey, List<String> errorKeys) {

    public SmsConfigViewModel {
        providerOptions = providerOptions == null ? List.of() : List.copyOf(providerOptions);
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
        errorKeys = errorKeys == null ? List.of() : List.copyOf(errorKeys);
    }

    /**
     * One credential field of the selected provider.
     *
     * @param name field name as the provider client declares it
     * @param set  whether a value is stored
     */
    public record CredentialField(String name, boolean set) {
    }
}
