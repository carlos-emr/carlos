/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Classic JSP text encoding")
@Tag("unit")
class JspPageEncodingRegressionTest {
    private static final Path JSP_ROOT = Path.of(System.getProperty("basedir", "."), "src/main/webapp/WEB-INF/jsp");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern UTF8_DIRECTIVE = Pattern.compile(
            "<%@\\s*page\\b[^%]*\\b(?:pageEncoding\\s*=\\s*['\"]UTF-8['\"]|charset\\s*=\\s*UTF-8\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALPHA_BULLET = Pattern.compile("^\\s*•\\S", Pattern.MULTILINE);

    @ParameterizedTest(name = "{0} renders {1}")
    @MethodSource("htmlLabels")
    void shouldKeepVisibleTextReadable_whenRenderingClassicJspMarkup(String jsp, String text) throws IOException {
        String html = StringEscapeUtils.unescapeHtml4(readTemplate(jsp));

        assertThat(html).as(jsp).contains(text);
    }

    private static Stream<Arguments> htmlLabels() {
        return Stream.of(
                arguments("fax/CoverPage.jsp", "Loading preview…</div>"),
                arguments("oscarResearch/oscarDxResearch/dxResearchEditQuickList.jsp",
                        "— ${carlos:forHtml(quickListName)}"),
                arguments("lab/DemographicLab.jsp", "\"dms.incomingDocs.select\"/> — <fmt:message"),
                arguments("provider/appointmentprovideradminday.jsp",
                        "\"schedule.scheduleflipview.title\"/> — <carlos:encode"),
                arguments("form/formBCAR2020pg1.jsp", ">Norwegian Bokmål</option>"),
                arguments("form/formbcarpg1.jsp", "ETHNIC ORIGIN OF NEWBORN’S"),
                arguments("billing/CA/ON/billingON3rdInv.jsp", "AMOUNT UNREADABLE — DO NOT REMIT."),
                arguments("billing/CA/ON/billingONHistory.jsp",
                        "A loader error truncated this list — please refresh or contact admin before"),
                arguments("billing/CA/ON/billingONHistorySpec.jsp",
                        "A loader error truncated this list — please refresh or contact admin."),
                arguments("billing/CA/ON/billingONMRI.jsp", "— per-provider transactions rolled back."),
                arguments("billing/CA/ON/billingONMRI.jsp", "— <carlos:encode value=\"${fp.causeMessage}\""),
                arguments("billing/CA/ON/billingONStatus.jsp", "amounts and were skipped — refresh, or contact admin"),
                arguments("billing/CA/ON/onGenRA.jsp", "RA import failed — see server log for details."),
                arguments("billing/CA/ON/onGenRAError.jsp",
                        "A loader error truncated this list — refresh, or contact admin if persistent."));
    }

    @ParameterizedTest(name = "{0} renders {1}")
    @MethodSource("scriptLabels")
    void shouldKeepScriptTextReadable_whenJspSuppliesALabel(String jsp, String text) throws IOException {
        String script = StringEscapeUtils.unescapeEcmaScript(readTemplate(jsp));

        assertThat(script).as(jsp).contains(text);
    }

    private static Stream<Arguments> scriptLabels() {
        return Stream.of(
                arguments("rx/SearchDrug3.jsp", "target.textContent = 'Checking drug status…';"),
                arguments("encounter/oscarMeasurements/HealthTrackerPage.jspf", "+ ' — Date: ' +"));
    }

    @Test
    void shouldShowEveryQuestionBullet_whenPrintingTheAlphaForm() throws IOException {
        String html = StringEscapeUtils.unescapeHtml4(readTemplate("provider/formALPHAprint1.jsp"));

        assertThat(ALPHA_BULLET.matcher(html).results().count()).isEqualTo(19);
        assertThat(html).doesNotContain("\uFFFD", "\u00EF\u00BF\u00BD");
    }

    private static String readTemplate(String jsp) throws IOException {
        byte[] bytes = Files.readAllBytes(JSP_ROOT.resolve(jsp));
        String template = JSP_COMMENT.matcher(new String(bytes, StandardCharsets.ISO_8859_1)).replaceAll("");
        if (UTF8_DIRECTIVE.matcher(template).find()) {
            return JSP_COMMENT.matcher(new String(bytes, StandardCharsets.UTF_8)).replaceAll("");
        }
        return template;
    }
}
