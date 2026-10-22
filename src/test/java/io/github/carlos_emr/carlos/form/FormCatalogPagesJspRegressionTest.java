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
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source checks for the form-catalog defects fixed together: the CESD return link (issue #4591), the Rourke 2006
 * reopen (the Rourke 2006 part of issue #4595) and the ALPHA page (issue #4590).
 *
 * @since 2026-10-22
 */
@DisplayName("Form catalog page regressions")
@Tag("unit")
@Tag("form")
class FormCatalogPagesJspRegressionTest {

    private static final Path FORM_JSPS = Path.of("src/main/webapp/WEB-INF/jsp/form");
    /** The page a form's Save redirects back to, posted as the hidden {@code form_link}. */
    private static final Pattern FORM_LINK = Pattern.compile("String formLink = \"([^\"]+)\"");

    private static String read(String page) throws IOException {
        return Files.readString(FORM_JSPS.resolve(page), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should name an existing page in every form's return link")
    void shouldNameExistingPage_inEveryFormLink() throws IOException {
        // CESD posted formCDSD.jsp, so every Save ended on a 404 after storing the row (issue #4591).
        List<String> missing = new ArrayList<>();
        int links = 0;
        try (Stream<Path> pages = Files.walk(FORM_JSPS)) {
            for (Path page : (Iterable<Path>) pages.filter(path -> path.toString().endsWith(".jsp"))::iterator) {
                Matcher link = FORM_LINK.matcher(Files.readString(page, StandardCharsets.UTF_8));
                while (link.find()) {
                    links++;
                    if (!Files.isRegularFile(FORM_JSPS.resolve(link.group(1)))) {
                        missing.add(FORM_JSPS.relativize(page) + " -> " + link.group(1));
                    }
                }
            }
        }
        assertThat(links).as("form pages declaring a return link").isGreaterThan(20);
        assertThat(missing).as("return links naming no page").isEmpty();
    }

    @Test
    @DisplayName("should return CESD to its own page after Save")
    void shouldReturnToCesdPage_afterSave() throws IOException {
        assertThat(read("formCESD.jsp")).contains("String formLink = \"formCESD.jsp\"").doesNotContain("formCDSD");
    }

    @ParameterizedTest(name = "formrourke2006{0}.jsp")
    @ValueSource(strings = {"p1", "p2", "p3", "p4"})
    @DisplayName("should quote the stored page on every Rourke 2006 page")
    void shouldQuoteLastVisitedPage_onEveryRourke2006Page(String page) throws IOException {
        // value=p1/> posted "p1/", and the reopen included formrourke2006p1/.jsp (HTTP 500, issue #4595).
        String source = read("formrourke2006" + page + ".jsp");
        assertThat(source)
                .contains("value=\"<carlos:encode value='<%= props.getProperty(\"c_lastVisited\", \"" + page
                        + "\") %>' context=\"htmlAttribute\"/>\"")
                .doesNotContainPattern("value=<%=\\s*props\\.getProperty\\(\"c_lastVisited\"");
    }

    @Test
    @DisplayName("should reopen Rourke 2006 through the page normalizer")
    void shouldReopenRourke2006_throughPageNormalizer() throws IOException {
        String wrapper = read("formrourke2006.jsp");
        assertThat(wrapper).contains("FrmRourke2006Record.reopenPage(props.getProperty(\"c_lastVisited\"))");
        assertThat(wrapper).doesNotContain("props.getProperty(\"c_lastVisited\", \"p1\")");
    }

    @Test
    @DisplayName("should encode every saved ALPHA text value")
    void shouldEncodeSavedText_onAlphaPage() throws IOException {
        // The page never opened before FrmAlphaRecord existed (issue #4590); once it does, a stored value with a quote,
        // < or </textarea> must come back as typed rather than end the attribute or the box.
        String source = read("formalpha.jsp");
        assertThat(source).doesNotContainPattern("value=\"<%=\\s*props\\.getProperty");
        assertThat(source).doesNotContainPattern("><%=\\s*props\\.getProperty\\([^)]*\\)\\s*%></textarea>");
        assertThat(source).contains(
                "><carlos:encode value='<%= props.getProperty(\"socialSupport\", \"\") %>'/></textarea>",
                "value=\"<carlos:encode value='<%= props.getProperty(\"other1Name\", \"\") %>' context=\"htmlAttribute\"/>\"");
    }

    @Test
    @DisplayName("should not link the ALPHA stylesheet that was never shipped")
    void shouldNotLinkMissingStylesheet_onAlphaPage() throws IOException {
        assertThat(read("formalpha.jsp")).doesNotContain("alphaStyle.css");
        assertThat(Path.of("src/main/webapp/form/alphaStyle.css")).doesNotExist();
    }
}
