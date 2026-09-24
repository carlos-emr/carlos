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
package io.github.carlos_emr.carlos.i18n;

import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsMessageBodyReadReason;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SMS history page builds its status, direction, purpose and reason labels from enum names
 * ({@code sms.status.SENT} and so on), so a new enum value without a key would show the raw key.
 */
@Tag("unit")
class SmsHistoryLabelKeysUnitTest {
    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};

    @Test
    @DisplayName("should define an SMS history label for every status, direction, purpose and reason in every locale")
    void shouldDefineLabel_forEveryEnumValueInEveryLocale() throws IOException {
        List<String> keys = new ArrayList<>();
        for (SmsStatus value : SmsStatus.values()) {
            keys.add("sms.status." + value.name());
        }
        for (SmsDirection value : SmsDirection.values()) {
            keys.add("sms.direction." + value.name());
        }
        for (SmsMessagePurpose value : SmsMessagePurpose.values()) {
            keys.add("sms.purpose." + value.name());
        }
        for (SmsMessageBodyReadReason value : SmsMessageBodyReadReason.values()) {
            keys.add("sms.history.reason." + value.name());
        }

        for (String locale : LOCALES) {
            Properties bundle = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/oscarResources_" + locale + ".properties")) {
                assertThat(in).as("oscarResources_%s.properties on the classpath", locale).isNotNull();
                bundle.load(in);
            }
            assertThat(keys).as("SMS history labels missing from oscarResources_%s.properties", locale)
                    .allSatisfy(key -> assertThat(bundle.getProperty(key)).as(key).isNotBlank());
        }
    }
}
