/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under GPL v2 or later.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("noteBrowser JSP security regression tests")
@Tag("unit")
@Tag("caseManagement")
class NoteBrowserJspSecurityRegressionTest {

    private static final Path NOTE_BROWSER = Path.of(
            "src/main/webapp/WEB-INF/jsp/casemgmt/noteBrowser.jsp");

    @Test
    @DisplayName("should not mutate documents directly from request parameters")
    void shouldNotMutateDocumentsDirectly_fromRequestParameters() throws Exception {
        String jsp = Files.readString(NOTE_BROWSER);

        assertThat(jsp)
                .doesNotContain("EDocUtil.deleteDocument(request.getParameter")
                .doesNotContain("EDocUtil.undeleteDocument(request.getParameter")
                .doesNotContain("EDocUtil.refileDocument(request.getParameter");
    }

    @Test
    @DisplayName("should post document mutations to dedicated note browser actions")
    void shouldPostDocumentMutations_toDedicatedActions() throws Exception {
        String jsp = Files.readString(NOTE_BROWSER);

        assertThat(jsp)
                .contains("/casemgmt/NoteBrowserDocumentDelete")
                .contains("/casemgmt/NoteBrowserDocumentUndelete")
                .contains("/casemgmt/NoteBrowserDocumentRefile");
    }
}
