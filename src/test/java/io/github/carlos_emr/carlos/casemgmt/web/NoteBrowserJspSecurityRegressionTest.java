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
import java.util.regex.Pattern;

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
                .doesNotContain("EDocUtil.deleteDocument(")
                .doesNotContain("EDocUtil.undeleteDocument(")
                .doesNotContain("EDocUtil.refileDocument(");
    }

    @Test
    @DisplayName("should post document mutations to dedicated note browser actions")
    void shouldPostDocumentMutations_toDedicatedActions() throws Exception {
        String jsp = Files.readString(NOTE_BROWSER);

        assertThat(jsp).containsIgnoringCase("<form name=\"DisplayDoc\" method=\"post\"");
        assertMutationFunctionPostsTo(jsp, "DeleteDoc", "/casemgmt/NoteBrowserDocumentDelete");
        assertMutationFunctionPostsTo(jsp, "UnDeleteDoc", "/casemgmt/NoteBrowserDocumentUndelete");
        assertMutationFunctionPostsTo(jsp, "RefileDoc", "/casemgmt/NoteBrowserDocumentRefile");
    }

    private void assertMutationFunctionPostsTo(String jsp, String functionName, String actionPath) {
        Pattern mutationPostPattern = Pattern.compile(
                "function\\s+" + Pattern.quote(functionName) + "\\s*\\(\\)\\s*\\{"
                        + "(?s:.*?)document\\.DisplayDoc\\.action\\s*=\\s*'[^']*"
                        + Pattern.quote(actionPath)
                        + "'(?s:.*?)document\\.DisplayDoc\\.submit\\s*\\(\\s*\\)",
                Pattern.MULTILINE);

        assertThat(mutationPostPattern.matcher(jsp).find()).isTrue();
    }
}
