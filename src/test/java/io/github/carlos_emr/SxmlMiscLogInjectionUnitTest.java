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

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Log-injection regression test for {@link SxmlMisc#createXmlDataString}.
 *
 * <p>The rejected-element-name warning logs an attacker-controlled HTTP request
 * parameter name. Because the value is logged precisely on the branch where it
 * failed the alphanumeric guard, it can carry CR/LF and forge additional log
 * lines. This test feeds a CRLF-laden parameter name and asserts the emitted log
 * message contains no raw line breaks (i.e. it was routed through
 * {@code LogSafe.sanitize}).</p>
 *
 * @since 2026-06-18
 */
@DisplayName("SxmlMisc log-injection Tests")
@Tag("unit")
class SxmlMiscLogInjectionUnitTest {

    /** Forged second log line an attacker would try to smuggle in via the param name. */
    private static final String FORGED = "INJECTED forged-admin-login-success";

    @Test
    @DisplayName("should sanitize rejected element name before logging")
    void shouldSanitizeRejectedElementName_whenParamNameContainsCrlf() {
        String maliciousName = "field_evil\r\n" + FORGED;
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameterNames()).thenReturn(Collections.enumeration(List.of(maliciousName)));
        when(req.getParameter(maliciousName)).thenReturn("someValue");

        try (LogCapture logCapture = LogCapture.forLogger(SxmlMisc.class)) {
            String result = SxmlMisc.createXmlDataString(req, "field_");

            // Invalid element name is skipped, so it never reaches the XML output.
            assertThat(result).doesNotContain(FORGED);

            assertThat(logCapture.messages()).hasSize(1);
            String logged = logCapture.messages().get(0);
            // No raw CR/LF means no forged log line can be smuggled through.
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            // The control chars survive as escaped sequences for diagnostics.
            assertThat(logged).contains("field_evil").contains("\\r\\n");
        }
    }
}
