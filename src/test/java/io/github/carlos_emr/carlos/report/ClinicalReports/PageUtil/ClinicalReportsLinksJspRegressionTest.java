/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.app.HttpMethodGuardFilter;
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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The Clinical Reports page's links and buttons reach their routes. A plain link is a GET, and
 * {@link HttpMethodGuardFilter} answers 405 to a GET of a route that changes something (such as
 * {@code report/RemoveClinicalReport}), so every link on the page must pass the filter, and "Del" and
 * "Clear" must be POST forms.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Clinical Reports page links and buttons")
class ClinicalReportsLinksJspRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/report/ClinicalReports.jsp");
    private static final Pattern CONTEXT_LINK =
            Pattern.compile("<a\\b[^>]*\\bhref=\"<%=\\s*request\\.getContextPath\\(\\)\\s*%>(/[^\"]*)\"");
    private static final String REMOVE_FORM =
            "<form method=\"post\" action=\"<%= request.getContextPath() %>/report/RemoveClinicalReport\"";

    @Test
    @DisplayName("should let every link on the page through the GET guard")
    void shouldPassTheGetGuard_forEveryLinkOnThePage() throws Exception {
        List<String> links = links();
        assertThat(links).as("the page's own links").isNotEmpty();

        var filter = new HttpMethodGuardFilter();
        filter.init(new MockFilterConfig());
        for (String link : links) {
            var request = get(link);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).as("GET %s", link).isNotEqualTo(405);
            assertThat(chain.getRequest()).as("GET %s reaches its route", link).isNotNull();
        }
    }

    @Test
    @DisplayName("should post Del and Clear as forms, Del naming the report by its key")
    void shouldPostDelAndClear_asForms() throws Exception {
        String page = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(page).doesNotContain("RemoveClinicalReport?");
        assertThat(page.split(Pattern.quote(REMOVE_FORM), -1)).hasSize(3);
        assertThat(page).contains("<input type=\"hidden\" name=\"clear\" value=\"yes\"/>");
        assertThat(page).contains("name=\"key\" value=\"<carlos:encode value='<%= re.getKey() %>' context=\"htmlAttribute\"/>\"");
    }

    /** The page's links to its own routes, with any scriptlet in the query (a list position) as 0. */
    private static List<String> links() throws Exception {
        Matcher matcher = CONTEXT_LINK.matcher(Files.readString(JSP, StandardCharsets.UTF_8));
        List<String> links = new ArrayList<>();
        while (matcher.find()) {
            links.add(matcher.group(1).replaceAll("<%=[^%]*%>", "0"));
        }
        return links;
    }

    private static MockHttpServletRequest get(String link) {
        String path = link.contains("?") ? link.substring(0, link.indexOf('?')) : link;
        var request = new MockHttpServletRequest("GET", "/carlos" + path);
        request.setContextPath("/carlos");
        if (link.contains("?")) {
            for (String pair : link.substring(link.indexOf('?') + 1).split("&")) {
                String[] parts = pair.split("=", 2);
                request.addParameter(parts[0], parts.length > 1 ? parts[1] : "");
            }
        }
        return request;
    }
}
