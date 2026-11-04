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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC billing pages' own links: the service-code tray list pages through its action, and the
 * reconciliation, dx-code and Teleplan code pages link only stylesheets that exist.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC billing page links")
class BcBillingPageLinksRegressionTest {

    private static final Path JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC");
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Pattern HREF = Pattern.compile("href=\"((?:<%=[^%]*%>|[^\"<])*)\"");
    private static final String CONTEXT_PATH = "<%= request.getContextPath() %>";

    private static String read(String page) throws IOException {
        return Files.readString(JSP_DIR.resolve(page), StandardCharsets.UTF_8);
    }

    /** The stylesheet {@code <link>} tags of a page, read past any {@code <%= ... %>} inside them. */
    private static List<String> stylesheetLinks(String page) {
        List<String> links = new ArrayList<>();
        for (int start = page.indexOf("<link"); start >= 0; start = page.indexOf("<link", start + 1)) {
            int end = start;
            while (end < page.length() && page.charAt(end) != '>') {
                end = page.startsWith("<%", end) ? page.indexOf("%>", end) + 2 : end + 1;
            }
            String tag = page.substring(start, Math.min(end + 1, page.length()));
            if (tag.contains("rel=\"stylesheet\"")) {
                links.add(tag);
            }
        }
        return links;
    }

    /** The page is reached by a forward, so the paging links need the action's address. */
    @Test
    @DisplayName("should page the tray-code list through its action, carrying only the page number")
    void shouldPageTrayList_throughItsAction() throws IOException {
        String page = read("billingSVCTrayAssoc.jsp");
        Matcher table = Pattern.compile("<display:table\\b[^>]*>").matcher(page);

        assertThat(table.find()).isTrue();
        assertThat(table.group()).contains("requestURI=\"/billing/CA/BC/supServiceCodeAssocAction\"");
        assertThat(table.group()).contains("excludedParams=\"*\"");
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml"), StandardCharsets.UTF_8))
                .containsSubsequence("<action name=\"billing/CA/BC/supServiceCodeAssocAction\"",
                        "/WEB-INF/jsp/billing/CA/BC/billingSVCTrayAssoc.jsp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"genTAS00.jsp", "genTAS00ByOfficeNo.jsp", "genTAS01.jsp", "genTAS22.jsp",
            "dxcode_svccode_assoc.jsp", "teleplan/ManageBillingCodes.jsp"})
    @DisplayName("should link only stylesheets that exist, by their address from the context root")
    void shouldLinkExistingStylesheets_fromContextRoot(String page) throws IOException {
        List<String> tags = stylesheetLinks(read(page));
        int links = 0;
        for (String tag : tags) {
            Matcher href = HREF.matcher(tag);
            assertThat(href.find()).as("%s: a stylesheet link without href", page).isTrue();
            String value = href.group(1);
            assertThat(value).as("%s: a page-relative stylesheet resolves against the action's address", page)
                    .startsWith(CONTEXT_PATH);
            Path file = WEBAPP.resolve(value.substring(CONTEXT_PATH.length()).replaceFirst("^/", ""));
            assertThat(file).as("%s links %s", page, value).exists();
            links++;
        }
        assertThat(links).as("%s links no stylesheet", page).isPositive();
    }
}
