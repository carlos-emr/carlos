/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
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

/**
 * Guards the eDoc document-report page's dynamically built mutation form.
 *
 * @since 2026-09-06
 */
@DisplayName("documentReport.jsp regressions")
@Tag("unit")
@Tag("documentManager")
class DocumentReportJspRegressionTest {

    private static final Path DOCUMENT_REPORT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "documentManager", "documentReport.jsp");

    /**
     * {@code submitDocAction()} creates a form and submits it in the same tick, so CSRFGuard's
     * injection never reaches it: its client script injects into the forms present when it runs,
     * and the {@code injectIntoDynamicNodes} observer has not fired yet. Without the token copied
     * across, every delete and undelete from this page is answered 403 ("Required Token is missing
     * from the Request") and the document silently stays put.
     */
    @Test
    @DisplayName("should copy the CSRF token into the dynamic document-action form")
    void shouldCopyCsrfToken_intoDynamicDocumentActionForm() throws IOException {
        String documentReport = Files.readString(DOCUMENT_REPORT_JSP, StandardCharsets.UTF_8);

        assertThat(documentReport)
                .contains("function appendCsrfToken(form) {")
                .contains("document.querySelector('input[name=\"CSRF-TOKEN\"]')");

        // The call has to sit inside submitDocAction, before the submit -- a defined-but-uncalled
        // helper looks identical to a fix and ships the same 403.
        int helperCall = documentReport.indexOf("appendCsrfToken(form);");
        int submit = documentReport.indexOf("form.submit();");
        assertThat(helperCall)
                .as("submitDocAction must call appendCsrfToken")
                .isGreaterThan(0);
        assertThat(helperCall)
                .as("the token must be appended before the form is submitted")
                .isLessThan(submit);
    }
}
