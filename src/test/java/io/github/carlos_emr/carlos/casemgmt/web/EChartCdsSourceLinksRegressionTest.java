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
package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The encounter chart once printed CDS import wording above the notes of every patient, imported or
 * not. These checks keep the space above the CPP boxes and the chart header free of it, and keep
 * what remains (two short links in the notes toolbar, for an imported patient only) behind the
 * presence decision. The rendered result for an ordinary and an imported patient is asserted in
 * {@code scripts/echart-cds-source-links-playwright-checks.js}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Encounter chart CDS source links")
class EChartCdsSourceLinksRegressionTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Path TAGS = WEBAPP.resolve("WEB-INF/tags");
    private static final Path CHART_TAG = TAGS.resolve("cdsChartSourceLinks.tag");
    private static final Path NOTES_COLUMN = WEBAPP.resolve("WEB-INF/jsp/casemgmt/newCaseManagementView.jsp");
    private static final Path HEADER = WEBAPP.resolve("WEB-INF/jsp/casemgmt/newEncounterHeader.jsp");
    private static final Path NOTES_PANEL = WEBAPP.resolve("WEB-INF/jsp/casemgmt/ChartNotes.jsp");
    private static final Path LEGACY_LAYOUT = WEBAPP.resolve("WEB-INF/jsp/casemgmt/CaseManagementView.jsp");
    private static final List<String> CHART_ONLY_KEYS = List.of(
            "sourceChartClinicalNotesLink", "sourceChartAdditionalCategoriesLink", "sourceClinicalNotesTimingHelp");

    @Test
    @DisplayName("should keep CDS wording out of the chart header and from above the CPP boxes")
    void shouldKeepCdsWordingOut_ofHeaderAndAboveCppBoxes() throws IOException {
        // Nothing may sit above the CPP boxes or lengthen the header: the links live in the notes toolbar.
        for (Path page : List.of(NOTES_COLUMN, HEADER)) {
            assertThat(Files.readString(page, StandardCharsets.UTF_8)).as("%s", page)
                    .doesNotContain("cdsImportMessages")
                    .doesNotContain("cds-note-source-timing")
                    .doesNotContain("<source:");
        }
        assertThat(Files.readString(NOTES_COLUMN, StandardCharsets.UTF_8)).contains("<div id=\"cppBoxes\">");
    }

    @Test
    @DisplayName("should place the gated chart tag in the notes toolbar and the legacy layout")
    void shouldPlaceGatedChartTag_inNotesToolbarAndLegacyLayout() throws IOException {
        String panel = Files.readString(NOTES_PANEL, StandardCharsets.UTF_8);
        // Beside the Filter button, in the row's free space, so the notes below do not move.
        assertThat(panel.indexOf("<source:cdsChartSourceLinks "))
                .isGreaterThan(panel.indexOf("id=\"notesFilterToggle\""))
                .isLessThan(panel.indexOf("<div id=\"filter\""));

        for (Path page : List.of(NOTES_PANEL, LEGACY_LAYOUT)) {
            String jsp = Files.readString(page, StandardCharsets.UTF_8);

            assertThat(jsp).as("%s places the gated chart tag and prints no CDS label or note of its own", page)
                    .contains("<source:cdsChartSourceLinks ")
                    .doesNotContain("cdsImportMessages")
                    .doesNotContain("cds-note-source-timing")
                    .doesNotContain("<source:cdsSourceFields ");
        }
    }

    @Test
    @DisplayName("should render the chart links through the gated tag with no explanatory paragraph")
    void shouldRenderChartLinks_throughGatedTagWithoutParagraph() throws IOException {
        String tag = Files.readString(CHART_TAG, StandardCharsets.UTF_8);
        String markup = tag.replaceAll("(?s)<%--.*?--%>", "");

        assertThat(markup)
                .contains("category=\"ClinicalNotes\"")
                .contains("category=\"NewCategory\"")
                // The wrapper is emitted by the gated tag, so an ordinary patient gets no markup at all.
                .contains("wrapperClass=\"cds-chart-source-links\"")
                .doesNotContain("<div")
                .doesNotContain("<span")
                .doesNotContain("<p")
                .doesNotContain("cds-note-source-timing")
                // A literal dash in a JSP without a declared page encoding once reached the browser as mojibake.
                .doesNotContain("\u2014")
                // The timing note is hover text on the clinical-notes link, not printed on the chart.
                .contains("hint=\"${cdsNotesTimingHelp}\"")
                .contains("<source:cdsSourceFields");
    }

    @Test
    @DisplayName("should gate the shared source-fields link on the presence decision")
    void shouldGateSharedLink_onPresenceDecision() throws IOException {
        String tag = Files.readString(TAGS.resolve("cdsSourceFields.tag"), StandardCharsets.UTF_8)
                .replaceAll("(?s)<%--.*?--%>", "");
        String gate = "<c:if test=\"${cdsSourceFieldsPresent}\">";
        int gateStart = tag.indexOf(gate);

        assertThat(tag).contains("CdsSourceFieldPresenceResolver.class");
        assertThat(gateStart).as("the presence gate exists").isPositive();
        assertThat(tag.indexOf("<c:if")).as("the presence gate is the outermost conditional").isEqualTo(gateStart);
        // Everything the tag can emit sits inside the gate's body: the gate is still open at the
        // link and at its optional wrapper, and it closes only at the end of the tag.
        assertThat(openConditionals(tag, tag.indexOf("<a class=\"cds-source-fields-link\""))).isPositive();
        assertThat(openConditionals(tag, tag.indexOf("<span class="))).isPositive();
        assertThat(openConditionals(tag, tag.indexOf("</span>"))).isPositive();
        assertThat(openConditionals(tag, tag.length())).isZero();
        assertThat(tag.substring(tag.lastIndexOf("</c:if>") + "</c:if>".length())).isBlank();
        assertThat(tag.substring(gateStart)).doesNotContain("<c:if test=\"${cdsSourceFieldsPresent}\"/>");
    }

    /** How many {@code <c:if>} bodies are open at a position; self-closing conditionals open none. */
    private static int openConditionals(String markup, int position) {
        assertThat(position).as("the element is present").isNotNegative();
        String before = markup.substring(0, position);
        int opened = 0;
        for (int at = before.indexOf("<c:if"); at >= 0; at = before.indexOf("<c:if", at + 1)) {
            int end = before.indexOf('>', at);
            if (end < 0 || before.charAt(end - 1) != '/') opened++;
        }
        int closed = 0;
        for (int at = before.indexOf("</c:if>"); at >= 0; at = before.indexOf("</c:if>", at + 1)) closed++;
        return opened - closed;
    }

    @Test
    @DisplayName("should keep the chart-only CDS messages out of every other page")
    void shouldKeepChartOnlyMessages_outOfEveryOtherPage() throws IOException {
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            List<Path> users = files.filter(Files::isRegularFile)
                    .filter(file -> { String name = file.getFileName().toString();
                        return name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag"); })
                    .filter(file -> !file.equals(CHART_TAG))
                    .filter(file -> { String text = read(file);
                        return CHART_ONLY_KEYS.stream().anyMatch(key -> text.contains("key=\"" + key + "\"")); })
                    .toList();

            assertThat(users).as("pages printing chart CDS wording outside the gated tag").isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            // Legacy pages are not all UTF-8; ISO-8859-1 reads any byte sequence and keeps ASCII keys intact.
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
