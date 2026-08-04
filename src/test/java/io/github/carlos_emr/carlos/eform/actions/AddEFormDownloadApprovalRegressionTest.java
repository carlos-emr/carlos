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
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the catch ordering that makes an incomplete download recoverable.
 *
 * <p>{@code EformContentUnavailableException} extends {@code PDFGenerationException}. If the general
 * handler is declared first the specific one becomes unreachable, the compiler accepts it in the
 * other order only, and an incomplete render silently collapses back into "could not be downloaded"
 * with no approval offered. That is the exact regression this file exists to catch, and it is
 * cheaper to assert on the source than to drive this very large action end to end.</p>
 *
 * @since 2026-07-26
 */
@DisplayName("eForm download approval regressions")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class AddEFormDownloadApprovalRegressionTest {

    private static final Path ADD_EFORM_ACTION = Path.of(
            "src", "main", "java", "io", "github", "carlos_emr", "carlos",
            "eform", "actions", "AddEForm2Action.java");

    @Test
    @DisplayName("should catch the incomplete-render exception before its superclass on every download branch")
    void shouldCatchIncompleteRender_beforeItsSuperclass() throws IOException {
        String source = Files.readString(ADD_EFORM_ACTION, StandardCharsets.UTF_8);

        // Both download branches — the new-save path and the update path — must offer the approval.
        assertThat(source.split("offerDownloadApproval\\(", -1).length - 1)
                .as("both download branches plus the helper declaration")
                .isGreaterThanOrEqualTo(3);

        // Ordering itself is enforced by javac: declaring the superclass first makes the subclass
        // catch unreachable and the build fails. What a future edit CAN silently do is delete the
        // specific catch entirely, which compiles cleanly and restores the dead end. So assert it is
        // present on each of the two download branches, next to the render call it guards.
        for (String fdidVariable : new String[] {"fdid", "prev_fdid"}) {
            String branch = "offerDownloadApproval(loggedInInfo, e, " + fdidVariable + ", demographic_no)";
            assertThat(source)
                    .as("download branch keyed on %s must offer the approval", fdidVariable)
                    .contains(branch);
        }
        // Both save-as-eDoc branches too: that path is refused by the same gate.
        assertThat(source.split("offerEDocApproval\\(", -1).length - 1)
                .as("both eDoc branches plus the helper declaration")
                .isGreaterThanOrEqualTo(3);
        assertThat(source)
                .as("the specific catch must guard the refusable renders")
                .contains("catch (EformContentUnavailableException e)");
    }

    @Test
    @DisplayName("should retry through the render-only route, never by re-posting the save action")
    void shouldRetryThroughRenderOnlyRoute_neverByResavingTheForm() throws IOException {
        // saveEformData persists a NEW eForm on every submit, so approving a render by re-posting
        // this action would duplicate the saved clinical record and would carry every form field,
        // patient data included, through the approval page as hidden inputs.
        String approvalPage = Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "jsp",
                "eform", "EFormRenderMissingContent.jsp"), StandardCharsets.UTF_8);

        // The retry target is supplied per path, so assert the page never posts back to the save
        // action and that both refusable paths name a render-only/archive-only route.
        assertThat(approvalPage).contains("${approvalAction}");
        assertThat(approvalPage).doesNotContain("/eform/addEForm");
        String action = Files.readString(ADD_EFORM_ACTION, StandardCharsets.UTF_8);
        assertThat(action).contains("\"eform/downloadEFormPdf\"");
        assertThat(action).contains("\"eform/saveEFormAsEDoc\"");
        // Every category the report carries must be listed: the approval digest binds to the
        // complete issue set, so an omitted category is one the clinician never saw.
        assertThat(approvalPage)
                .contains("failedContentResources")
                .contains("excludedContentElements")
                .contains("signatureMissing")
                .contains("providerStampMissing")
                .contains("timerCompatibilityFailure")
                .contains("severeConsoleErrors")
                .contains("containedInteractions")
                .contains("stabilizationCapped")
                .contains("labDecisionSupportStubbed");
    }
}
