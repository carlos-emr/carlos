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
package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Log-injection regression test for {@link HRMDocumentDao}'s ORDER BY validation.
 *
 * <p>{@code isValidOrderRequest} logs the rejected {@code orderColumn} /
 * {@code orderDirection} sort parameters. These are user-supplied request values
 * logged on the rejection path, so they can carry CR/LF and forge log lines. The
 * private method is exercised reflectively (the public callers need an
 * EntityManager) to assert the rejected values are sanitized before logging.</p>
 *
 * @since 2026-06-18
 */
@DisplayName("HRMDocumentDao log-injection Tests")
@Tag("unit")
class HRMDocumentDaoLogInjectionUnitTest {

    private static final String FORGED = "INJECTED forged-admin-login-success";

    private static boolean invokeIsValidOrderRequest(String orderColumn, String orderDirection) throws Exception {
        HRMDocumentDao dao = new HRMDocumentDao();
        Method method = HRMDocumentDao.class.getDeclaredMethod("isValidOrderRequest", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(dao, orderColumn, orderDirection);
    }

    @Test
    @DisplayName("should sanitize rejected orderColumn before logging")
    void shouldSanitizeOrderColumn_whenNotInAllowlist() throws Exception {
        String maliciousColumn = "id\r\n" + FORGED;

        try (LogCapture logCapture = LogCapture.forLogger(HRMDocumentDao.class)) {
            boolean valid = invokeIsValidOrderRequest(maliciousColumn, "ASC");

            assertThat(valid).isFalse();
            assertThat(logCapture.messages()).hasSize(1);
            String logged = logCapture.messages().get(0);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("orderColumn").contains("\\r\\n");
        }
    }

    @Test
    @DisplayName("should sanitize rejected orderDirection before logging")
    void shouldSanitizeOrderDirection_whenNotAscOrDesc() throws Exception {
        String maliciousDirection = "ASC\r\n" + FORGED;

        try (LogCapture logCapture = LogCapture.forLogger(HRMDocumentDao.class)) {
            // A valid allowlisted column lets validation reach the direction check.
            boolean valid = invokeIsValidOrderRequest("reportDate", maliciousDirection);

            assertThat(valid).isFalse();
            assertThat(logCapture.messages()).hasSize(1);
            String logged = logCapture.messages().get(0);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("orderDirection").contains("\\r\\n");
        }
    }
}
