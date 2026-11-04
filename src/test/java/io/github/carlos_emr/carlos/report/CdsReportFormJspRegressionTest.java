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
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CDS Report panel is loaded into the administration page, which provides jQuery and the jQuery
 * Validation plugin. A second jQuery loaded by the panel replaced the page's copy and dropped the
 * plugin, so the panel's own validate() call threw (issue #4885).
 *
 * @since 2026-10-17
 */
@DisplayName("CDS Report panel JSP regressions")
@Tag("unit")
@Tag("report")
class CdsReportFormJspRegressionTest {

    private static final Path FORM = Path.of("src/main/webapp/WEB-INF/jsp/oscarReport/cds_4_report_form.jsp");

    private static final String PAGE_ONLY = "<% if (!RequestNegotiation.isAjax(request)) { %>";
    private static final String[] SCRIPTS = {
        "<script src=\"${ctx}/library/jquery/jquery-3.7.1.min.js\"></script>",
        "<script src=\"${ctx}/library/jquery/jquery-compat.js\"></script>",
        "<script src=\"${ctx}/library/flatpickr/flatpickr.min.js\"></script>",
        "<script src=\"${ctx}/library/jquery/jquery.validate-1.21.0.min.js\"></script>",
    };

    @Test
    void shouldNotLoadItsOwnJquery_forThePanelOfTheAdministrationPage() throws IOException {
        String form = Files.readString(FORM, StandardCharsets.UTF_8);

        // Every script include sits inside the block that runs only when the form is not a panel (AJAX) load.
        int open = form.indexOf(PAGE_ONLY);
        assertThat(open).as("the page-only block").isGreaterThanOrEqualTo(0);
        int close = form.indexOf("<% } %>", open);
        assertThat(close).isGreaterThan(open);
        String block = form.substring(open, close);
        String outside = form.substring(0, open) + form.substring(close);
        for (String script : SCRIPTS) {
            assertThat(block).contains(script);
            assertThat(outside).doesNotContain(script);
        }
        assertThat(form).contains("$('#cdsForm').validate(");
    }

    @Test
    void shouldLoadWhatItNeeds_whenOpenedAsAPageOfItsOwn() throws IOException {
        String form = Files.readString(FORM, StandardCharsets.UTF_8);

        // The legacy Administration page links the form as a plain page: the scripts, including the Validation
        // plugin, come with it, and the administration page's registerFormSubmit is only called when present.
        assertThat(form).contains(PAGE_ONLY);
        assertThat(form).contains("if (typeof registerFormSubmit === 'function') {");
        assertThat(form).doesNotContain("\n    registerFormSubmit('cdsForm', 'cds-results');\n</script>");
    }

    @Test
    void shouldLeaveTheUnnamedFilterChooserOutOfValidation_forTheCdsForm() throws IOException {
        String form = Files.readString(FORM, StandardCharsets.UTF_8);

        // With validation running again, the plugin would log an error for this unnamed select on every submit.
        assertThat(form).contains("<select id=\"filterCriteriaSelection\"");
        assertThat(form).contains("ignore: ':hidden, #filterCriteriaSelection'");
    }
}
