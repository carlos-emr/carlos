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
 * not. These checks keep that wording behind the presence decision; the rendered result for an
 * imported and an ordinary patient is asserted in
 * {@code scripts/demographic-cds-import-playwright-checks.js}.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Encounter chart CDS source links")
class EChartCdsSourceLinksRegressionTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Path TAGS = WEBAPP.resolve("WEB-INF/tags");
    private static final List<Path> CHART_LAYOUTS = List.of(
            WEBAPP.resolve("WEB-INF/jsp/casemgmt/newCaseManagementView.jsp"),
            WEBAPP.resolve("WEB-INF/jsp/casemgmt/CaseManagementView.jsp"));
    private static final List<String> CHART_ONLY_KEYS =
            List.of("sourceClinicalNotes", "sourceAdditionalCategories", "sourceClinicalNotesTimingHelp");

    @Test
    @DisplayName("should leave CDS wording to the gated chart tag in both chart layouts")
    void shouldLeaveCdsWordingToGatedTag_inBothChartLayouts() throws IOException {
        for (Path layout : CHART_LAYOUTS) {
            String jsp = Files.readString(layout, StandardCharsets.UTF_8);

            assertThat(jsp).as("%s places the gated chart tag", layout).contains("<source:cdsChartSourceLinks ");
            assertThat(jsp).as("%s prints no CDS label or note of its own", layout)
                    .doesNotContain("cdsImportMessages")
                    .doesNotContain("cds-note-source-timing")
                    .doesNotContain("<source:cdsSourceFields ");
        }
    }

    @Test
    @DisplayName("should print the chart labels and timing note only inside presence checks")
    void shouldPrintChartLabelsAndTimingNote_onlyInsidePresenceChecks() throws IOException {
        String tag = Files.readString(TAGS.resolve("cdsChartSourceLinks.tag"), StandardCharsets.UTF_8);

        assertThat(tag).contains("hasSourceFields(request, cdsPatient, \"ClinicalNotes\")")
                .contains("hasSourceFields(request, cdsPatient, \"NewCategory\")");
        int outerGate = tag.indexOf("<c:if test=\"${cdsNotesPresent or cdsAdditionalPresent}\">");
        assertThat(outerGate).as("the whole block sits inside one presence check").isPositive();
        assertThat(tag.indexOf("cdsImportMessages")).as("no wording before the presence check").isGreaterThan(outerGate);
        assertThat(tag).contains("<c:if test=\"${cdsNotesPresent}\"><p class=\"cds-note-source-timing\">");
        // A literal dash in a JSP without a declared page encoding once reached the browser as mojibake.
        assertThat(tag).contains("&mdash;").doesNotContain("—");
    }

    @Test
    @DisplayName("should gate the shared source-fields link on the presence decision")
    void shouldGateSharedLink_onPresenceDecision() throws IOException {
        String tag = Files.readString(TAGS.resolve("cdsSourceFields.tag"), StandardCharsets.UTF_8);

        int gate = tag.indexOf("<c:if test=\"${cdsSourceFieldsPresent}\">");
        assertThat(tag).contains("CdsSourceFieldPresenceResolver.class");
        assertThat(gate).isPositive();
        assertThat(tag.indexOf("<a class=\"cds-source-fields-link\"")).isGreaterThan(gate);
    }

    @Test
    @DisplayName("should keep the chart-only CDS messages out of every other page")
    void shouldKeepChartOnlyMessages_outOfEveryOtherPage() throws IOException {
        Path chartTag = TAGS.resolve("cdsChartSourceLinks.tag");
        try (Stream<Path> files = Files.walk(WEBAPP)) {
            List<Path> users = files.filter(Files::isRegularFile)
                    .filter(file -> { String name = file.getFileName().toString();
                        return name.endsWith(".jsp") || name.endsWith(".jspf") || name.endsWith(".tag"); })
                    .filter(file -> !file.equals(chartTag))
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
