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

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SMS queue page ({@code admin/smsQueue.jsp} and its row fragment) builds its status and scheduler-outcome
 * labels from enum names ({@code sms.queue.status.FAILED}, {@code sms.queue.scheduler.outcome.COMPLETED}) and its
 * time period labels from their parameter values ({@code sms.queue.window.option.30d}), so a new enum value
 * without a key would show the raw key. Its fixed labels and the Administration menu entry must be
 * in every locale too.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("i18n")
@DisplayName("SMS queue label keys")
class SmsQueueLabelKeysUnitTest {
    private static final String[] LOCALES = {"en", "fr", "es", "pt_BR", "pl"};
    private static final List<Path> PAGES = List.of(
            Path.of("src/main/webapp/WEB-INF/jsp/admin/smsQueue.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/admin/smsQueueRows.jspf"));
    // Literal keys only; keys built from EL are covered by the enum loops below.
    private static final Pattern LITERAL_KEY = Pattern.compile("key=\"([^\"$]+)\"");

    @Test
    @DisplayName("should define a label for every SMS status, scheduler outcome and time period in every locale")
    void shouldDefineLabel_forEveryEnumValueInEveryLocale() throws IOException {
        List<String> keys = new ArrayList<>();
        keys.add("sms.queue.status.BLOCKED_BY_CONSENT");
        for (SmsStatus status : SmsStatus.values()) {
            keys.add("sms.queue.status." + status.name());
        }
        for (SmsQueueScheduler.RunOutcome outcome : SmsQueueScheduler.RunOutcome.values()) {
            keys.add("sms.queue.scheduler.outcome." + outcome.name());
        }
        for (SmsQueueWindow window : SmsQueueWindow.values()) {
            keys.add("sms.queue.window.option." + window.parameterValue());
        }

        assertDefinedInEveryLocale(keys);
    }

    @Test
    @DisplayName("should define every fixed label the SMS queue page and the menu use in every locale")
    void shouldDefineLabel_forEveryLiteralKeyInEveryLocale() throws IOException {
        List<String> keys = new ArrayList<>();
        keys.add("admin.admin.smsQueue");
        for (Path page : PAGES) {
            Matcher matcher = LITERAL_KEY.matcher(Files.readString(page));
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        assertThat(keys).as("literal keys found in the SMS queue page").hasSizeGreaterThan(20);

        assertDefinedInEveryLocale(keys);
    }

    private void assertDefinedInEveryLocale(List<String> keys) throws IOException {
        for (String locale : LOCALES) {
            Properties bundle = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/oscarResources_" + locale + ".properties")) {
                assertThat(in).as("oscarResources_%s.properties on the classpath", locale).isNotNull();
                bundle.load(in);
            }
            assertThat(keys).as("SMS queue labels missing from oscarResources_%s.properties", locale)
                    .allSatisfy(key -> assertThat(bundle.getProperty(key)).as(key).isNotBlank());
        }
    }
}
