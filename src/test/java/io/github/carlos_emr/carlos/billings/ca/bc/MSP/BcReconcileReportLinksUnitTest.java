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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC MSP Reconciliation Reports ({@code viewReconcileReports.jsp}) writes {@code <base href="<context>/">}, so its
 * report links, written relative ({@code createBillingReportAction?…}), resolved to
 * {@code /<context>/createBillingReportAction}, which no action answers, instead of the action's route
 * {@code /<context>/billing/CA/BC/createBillingReportAction}. The Billed and remittance-summary links, each as PDF and
 * CSV, answered 404 that way (issue #5063; the same class as #4930).
 *
 * @since 2026-10-19
 */
@DisplayName("BC reconciliation list report links")
@Tag("unit")
@Tag("billing")
class BcReconcileReportLinksUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/viewReconcileReports.jsp");
    private static final Path STRUTS = Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");
    /** The base the page writes: the context root. */
    private static final Pattern CONTEXT_ROOT_BASE = Pattern.compile("<base href=[^>]*getContextPath\\(\\)\\s*\\+\\s*\"/\"");
    /** Every link to the report action, with what comes before the action's name in its href. */
    private static final Pattern REPORT_LINK = Pattern.compile("href=\"([^\"]*?)createBillingReportAction\\?[^\"]*repType=(\\w+)");
    /** What goes before the action's route in a link that resolves under any base: the context path, as scriptlet or EL. */
    private static final List<String> CONTEXT_PATH = List.of("<%= request.getContextPath() %>/", "${pageContext.request.contextPath}/");

    /** The source without its HTML and JSP comments: a commented-out link is never followed. */
    private static String withoutComments(String source) {
        StringBuilder live = new StringBuilder(source.length());
        int at = 0;
        while (at < source.length()) {
            int html = source.indexOf("<!--", at);
            int jsp = source.indexOf("<%--", at);
            int start = html < 0 ? jsp : (jsp < 0 ? html : Math.min(html, jsp));
            if (start < 0) {
                live.append(source, at, source.length());
                break;
            }
            live.append(source, at, start);
            String close = start == jsp ? "--%>" : "-->";
            int end = source.indexOf(close, start + 4);
            at = end < 0 ? source.length() : end + close.length();
        }
        return live.toString();
    }

    private static String page() throws IOException {
        return withoutComments(Files.readString(PAGE, StandardCharsets.UTF_8));
    }

    /** The route struts-billing.xml maps CreateBillingReport2Action to. */
    private static String reportRoute() throws IOException {
        Matcher action = Pattern.compile("<action name=\"([^\"]+)\" class=\"[^\"]*\\.CreateBillingReport2Action\"")
                .matcher(Files.readString(STRUTS, StandardCharsets.UTF_8));
        assertThat(action.find()).as("struts-billing.xml maps CreateBillingReport2Action").isTrue();
        return action.group(1);
    }

    @Test
    void shouldLinkEveryReport_toTheActionsRouteUnderTheContextPath() throws IOException {
        String source = page();
        assertThat(CONTEXT_ROOT_BASE.matcher(source).find()).as("the page writes the context-root base").isTrue();
        String route = reportRoute();
        assertThat(route).isEqualTo("billing/CA/BC/createBillingReportAction");

        List<String> links = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Matcher link = REPORT_LINK.matcher(source);
        while (link.find()) {
            links.add(link.group(2));
            String prefix = link.group(1);
            if (CONTEXT_PATH.stream().noneMatch(path -> prefix.equals(path + "billing/CA/BC/"))) {
                wrong.add(link.group(2) + " via \"" + link.group(1) + "\"");
            }
        }
        assertThat(links).as("the Billed and remittance-summary links, each as PDF and CSV")
                .containsExactly("REP_MSPREM", "REP_MSPREM", "REP_MSPREMSUM", "REP_MSPREMSUM");
        assertThat(wrong).as("report links that resolve under the base to /<context>/createBillingReportAction").isEmpty();
    }
}
