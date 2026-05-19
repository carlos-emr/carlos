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
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the pending documents queue page.
 *
 * @since 2026-05-04
 */
@DisplayName("Pending documents JSP regressions")
@Tag("unit")
@Tag("document")
public class PendingDocumentsJspRegressionTest {

    private static final Path PENDING_DOCUMENTS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/oscarMDS/documentsInQueues.jsp");
    private static final Path STRUTS_DOCUMENT_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-document.xml");
    private static final Map<String, Path> LOCALE_BUNDLES = Map.of(
            "en", Path.of("src/main/resources/oscarResources_en.properties"),
            "fr", Path.of("src/main/resources/oscarResources_fr.properties"),
            "es", Path.of("src/main/resources/oscarResources_es.properties"),
            "pt_BR", Path.of("src/main/resources/oscarResources_pt_BR.properties"),
            "pl", Path.of("src/main/resources/oscarResources_pl.properties")
    );
    private static final List<String> REQUIRED_I18N_KEYS = List.of(
            "inboxmanager.documentsInQueues",
            "inboxmanager.document.queues",
            "inboxmanager.document.documents",
            "global.btnBack",
            "global.hl7"
    );

    @Test
    @DisplayName("pending documents page chrome should use i18n message keys")
    void shouldUseI18nMessages_forPendingDocumentsPageLabels() throws IOException {
        String jsp = Files.readString(PENDING_DOCUMENTS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<fmt:message key=\"inboxmanager.documentsInQueues\"/>")
                .contains("<fmt:message key=\"global.btnBack\"/>")
                .contains("<fmt:message key=\"inboxmanager.document.queues\"/>")
                .contains("<fmt:message key=\"inboxmanager.document.documents\"/>")
                .contains("<fmt:message key=\"global.hl7\" var=\"hl7Label\"/>")
                .contains("function normalizePatientNumber(patientId)")
                .contains("var safePatientId = normalizePatientNumber(patientId);")
                .doesNotContain(">Back</button>")
                .doesNotContain(">Queues</th>")
                .doesNotContain(">Documents</th>")
                .doesNotContain("title=\"HL7s\">HL7s");
    }

    @Test
    @DisplayName("pending documents focus should wait until the document description field exists")
    void shouldGuardFocus_whenDocumentDescriptionHasNotRenderedYet() throws IOException {
        String jsp = Files.readString(PENDING_DOCUMENTS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("var docDesc = $('docDesc_' + current_first_doclab);")
                .contains("if (docDesc) {")
                .contains("docDesc.focus();")
                .doesNotContain("$('docDesc_' + current_first_doclab).focus();");
    }

    @Test
    @DisplayName("pending documents patient ID normalization should accept only non-negative numeric values")
    void shouldRejectNonNumericValues_whenNormalizingPatientIds() {
        assertThat(normalizePatientNumber("123")).isEqualTo("123");
        assertThatThrownBy(() -> normalizePatientNumber("-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizePatientNumber("location"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizePatientNumber("1<script>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pending documents should load documents through the routed show document action")
    void shouldLoadDocumentDetails_throughRoutedShowDocumentAction() throws IOException {
        String jsp = Files.readString(PENDING_DOCUMENTS_JSP, StandardCharsets.UTF_8);
        String struts = Files.readString(STRUTS_DOCUMENT_XML, StandardCharsets.UTF_8);

        assertThat(jsp).contains("/documentManager/ViewShowDocument");
        assertThat(struts)
                .contains("<action name=\"documentManager/ViewShowDocument\"")
                .contains("/WEB-INF/jsp/documentManager/showDocument.jsp");
    }

    @Test
    @DisplayName("pending documents i18n keys should resolve in every shipped locale")
    void shouldDefineI18nKeys_forPendingDocumentsLabelsInEveryLocale() throws IOException {
        for (Map.Entry<String, Path> localeBundle : LOCALE_BUNDLES.entrySet()) {
            assertThat(invalidI18nKeys(localeBundle.getValue()))
                    .as("oscarResources_%s.properties should define non-blank pending documents labels", localeBundle.getKey())
                    .isEmpty();
        }
    }

    private static String normalizePatientNumber(String patientId) {
        if (!patientId.matches("-?\\d+")) {
            throw new IllegalArgumentException("Invalid patient id");
        }
        return patientId;
    }

    private static List<String> invalidI18nKeys(Path bundlePath) throws IOException {
        Properties bundle = loadBundle(bundlePath);
        return REQUIRED_I18N_KEYS.stream()
                .filter(key -> bundle.getProperty(key, "").isBlank())
                .toList();
    }

    private static Properties loadBundle(Path bundlePath) throws IOException {
        Properties bundle = new Properties();
        try (var reader = Files.newBufferedReader(bundlePath, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
