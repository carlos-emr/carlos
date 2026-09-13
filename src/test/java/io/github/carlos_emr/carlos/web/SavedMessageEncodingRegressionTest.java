/*
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This file is part of CARLOS (https://github.com/carlos-emr/carlos).
 *
 * CARLOS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * CARLOS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CARLOS. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for JSP saved-message HTML encoding.
 *
 * @since 2026-05-30
 */
@DisplayName("Saved message JSP encoding")
@Tag("unit")
@Tag("regression")
@Tag("security")
class SavedMessageEncodingRegressionTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final String CARLOS_TAGLIB = "<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>";
    private static final String ENCODED_SAVED_MESSAGE = "${carlos:forHtml(savedMessage)}";
    private static final String RAW_SAVED_MESSAGE = "${savedMessage}";
    private static final Path JSP_ROOT = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));

    @Test
    @DisplayName("should encode savedMessage in sites admin detail")
    void shouldEncodeSavedMessage_inSitesAdminDetail() throws IOException {
        assertSavedMessageEncoded("admin/sitesAdminDetail.jsp");
    }

    @Test
    @DisplayName("should encode savedMessage in BPMH form")
    void shouldEncodeSavedMessage_inBpmhForm() throws IOException {
        assertSavedMessageEncoded("form/pharmaForms/formBPMH.jsp");
    }

    private static void assertSavedMessageEncoded(String relativePath) throws IOException {
        String jsp = Files.readString(JSP_ROOT.resolve(relativePath), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains(CARLOS_TAGLIB)
                .contains(ENCODED_SAVED_MESSAGE)
                .doesNotContain(RAW_SAVED_MESSAGE);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
