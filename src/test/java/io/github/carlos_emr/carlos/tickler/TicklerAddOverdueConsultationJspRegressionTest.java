/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.tickler;

import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #4999. The Consultations list's overdue "Add ticklers" link and the Add Tickler form are
 * two pages that only meet through a query string, so nothing in the compiler noticed when the
 * list kept sending {@code demo} and {@code message} (the names of the retired bulk action) while
 * {@code ticklerAdd.jsp} read {@code demographic_no} and started its textarea empty. The staff who
 * confirmed the follow-up got a blank task form.
 *
 * <p>These tests pin the meeting point from both sides: every parameter the link carries must be
 * one the form reads, and the form must put the suggested message where the clinician can see and
 * submit it. The rendered behaviour is covered by the {@code consultation-overdue-tickler} browser
 * check.</p>
 */
@DisplayName("Overdue consultation Add Tickler handoff (#4999)")
@Tag("unit")
@Tag("tickler")
class TicklerAddOverdueConsultationJspRegressionTest {

    private static final Path TICKLER_ADD_JSP = Path.of("src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp");
    private static final Path CONSULTATION_LIST_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ViewConsultationRequests.jsp");

    @Test
    @DisplayName("should read every parameter the overdue link carries in the Add Tickler form")
    void shouldReadEveryLinkParameter_inTicklerAddForm() throws IOException {
        String form = Files.readString(TICKLER_ADD_JSP, StandardCharsets.UTF_8);
        String link = EctViewConsultationRequestsUtil.overdueTicklerPath("/carlos", "42");

        List<String> names = new ArrayList<>();
        for (String pair : link.substring(link.indexOf('?') + 1).split("&")) {
            names.add(URLDecoder.decode(pair.substring(0, pair.indexOf('=')), StandardCharsets.UTF_8));
        }

        assertThat(names).isNotEmpty();
        for (String name : names) {
            assertThat(form).as("ticklerAdd.jsp must read the '%s' parameter the overdue link sends", name)
                    .contains("request.getParameter(\"" + name + "\")");
        }
    }

    @Test
    @DisplayName("should start the message textarea from the suggested message, encoded for HTML content")
    void shouldFillMessageTextarea_fromEncodedSuggestion() throws IOException {
        String form = Files.readString(TICKLER_ADD_JSP, StandardCharsets.UTF_8);

        // The textarea is written inline: any whitespace between the tags would become part of the
        // submitted text.
        assertThat(form)
                .contains("String suggestedMessage = Objects.toString(request.getParameter(\"ticklerMessage\"), \"\");")
                .contains("<textarea name=\"ticklerMessage\" id=\"ticklerMessage\" class=\"form-control\">"
                        + "<carlos:encode value='<%= suggestedMessage %>' context=\"html\"/></textarea>")
                .doesNotContain("class=\"form-control\"></textarea>");
    }

    @Test
    @DisplayName("should associate the Reminder Message text with its box so assistive technology names it")
    void shouldLabelMessageTextarea_forAssistiveTechnology() throws IOException {
        String form = Files.readString(TICKLER_ADD_JSP, StandardCharsets.UTF_8);

        // SonarCloud Web:InputWithoutLabelCheck: the caption sat in a neighbouring cell with no
        // programmatic link to the textarea.
        assertThat(form).contains("<td class=\"tickler-label\"><label for=\"ticklerMessage\">"
                + "<fmt:message key=\"tickler.ticklerAdd.formReminder\"/>:</label></td>");
    }

    @Test
    @DisplayName("should build the overdue link with the helper instead of hand-written parameter names")
    void shouldBuildOverdueLink_withHelper() throws IOException {
        String list = Files.readString(CONSULTATION_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(list)
                .contains("EctViewConsultationRequestsUtil.overdueTicklerPath(")
                .doesNotContain("\"demo=").doesNotContain("&demo=").doesNotContain("&message=");
    }
}
