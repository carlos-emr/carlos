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
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Log-injection regression test for {@link CarlosProperties#getProperty(String)}.
 *
 * <p>Both warning branches (missing key, blacklisted value) interpolate untrusted
 * values — the caller-supplied key and a properties-file value — into the log
 * message. This test drives each branch with CRLF-laden input and asserts the
 * emitted message carries no raw line breaks (routed through
 * {@code LogSafe.sanitize}).</p>
 *
 * @since 2026-06-18
 */
@DisplayName("CarlosProperties log-injection Tests")
@Tag("unit")
class CarlosPropertiesLogInjectionUnitTest {

    private static final String FORGED = "INJECTED forged-admin-login-success";

    /** Keys set on the shared singleton; removed after each test to avoid leakage. */
    private String addedKey;

    @AfterEach
    void cleanup() {
        if (addedKey != null) {
            CarlosProperties.getInstance().remove(addedKey);
            addedKey = null;
        }
    }

    @Test
    @DisplayName("should sanitize key when property is missing")
    void shouldSanitizeKey_whenPropertyMissing() {
        String maliciousKey = "missing.key\r\n" + FORGED;

        try (LogCapture logCapture = LogCapture.forLogger(CarlosProperties.class)) {
            CarlosProperties.getInstance().getProperty(maliciousKey);

            String logged = lastWarn(logCapture);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("\\r\\n");
        }
    }

    @Test
    @DisplayName("should sanitize key and value when value is blacklisted")
    void shouldSanitizeKeyAndValue_whenValueBlacklisted() {
        addedKey = "blacklisted.key\r\n" + FORGED;
        // Value begins with the deprecated "oscar." namespace -> blacklist branch fires.
        String maliciousValue = "oscar.LegacyClass\r\n" + FORGED;
        CarlosProperties.getInstance().setProperty(addedKey, maliciousValue);

        try (LogCapture logCapture = LogCapture.forLogger(CarlosProperties.class)) {
            CarlosProperties.getInstance().getProperty(addedKey);

            String logged = lastWarn(logCapture);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            // Both the key and the blacklisted value are escaped in the warning.
            assertThat(logged).contains("blacklisted.key").contains("oscar.LegacyClass");
            assertThat(logged).contains("\\r\\n");
        }
    }

    /** Returns the most recent captured message, asserting at least one was emitted. */
    private static String lastWarn(LogCapture logCapture) {
        assertThat(logCapture.messages()).isNotEmpty();
        return logCapture.messages().get(logCapture.messages().size() - 1);
    }
}
