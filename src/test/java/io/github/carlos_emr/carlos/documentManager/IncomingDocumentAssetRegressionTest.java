/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Source-level regression pins for incoming-document queue controls. */
@DisplayName("Incoming document UI assets")
@Tag("unit")
@Tag("fast")
@Tag("document")
class IncomingDocumentAssetRegressionTest {

    private static final Path SHOW_DOCUMENT_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "showDocument.jsp");
    private static final Path INCOMING_DOCS_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "incomingDocs.jsp");
    private static final Path DOCUMENT_JS = Path.of(
            "src", "main", "webapp", "share", "javascript", "oscarMDSIndex.js");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should disable refile for every queue that already contains the document")
    void shouldTrackEveryAlreadyRefiledQueue() throws IOException {
        String jsp = read(SHOW_DOCUMENT_JSP);
        String javascript = read(DOCUMENT_JS);

        assertThat(jsp)
                .contains("Set<Integer> docFiledQueues")
                .contains("docFiledQueues.add(id)")
                .contains("data-already-refiled=")
                .doesNotContain("docCurrentFiledQueue");
        assertThat(javascript)
                .contains("selectedQueue.getAttribute(\"data-already-refiled\") === \"true\"");
    }

    @Test
    @DisplayName("should not file the source document when refile fails")
    void shouldRequireSuccessfulRefileResponseBeforeFilingSource() throws IOException {
        String javascript = read(DOCUMENT_JS);

        assertThat(javascript)
                .contains("if (!response.ok)")
                .contains("Unable to refile document (HTTP ")
                .contains("alert('Unable to refile document. Please try again.')")
                .containsSubsequence("if (!response.ok)", "return response.text()", "fileDoc(id)");
    }

    @Test
    @DisplayName("should expose the disabled Save explanation beyond pointer hover")
    void shouldExposeDisabledSaveExplanationAccessibly() throws IOException {
        String jsp = read(INCOMING_DOCS_JSP);

        assertThat(jsp)
                .contains("id=\"save-disabled-help\"")
                .contains("aria-describedby=\"save-disabled-help\"")
                .contains("saveObj.setAttribute('aria-describedby', 'save-disabled-help')")
                .contains("saveHelp.hidden = false")
                .contains("saveHelp.hidden = true");
    }
}
