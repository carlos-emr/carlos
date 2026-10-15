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
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the inner gap between the printed Patient Encounter Worksheet text and the template's border.
 *
 * <p>{@link FrmPDFServlet} places each field at the coordinates in
 * {@code patientEncounterWorksheetCfg.txt}. The worksheet template's outer border lines sit at
 * x = 30.25 pt and x = 581.75 pt, and its "Allergies:" and "Encounter Notes:" labels start at
 * x = 36 pt. The allergies line and the encounter-notes column used to start at x = 32 pt, and the
 * notes column ran to x = 580 pt, so printed text sat against the border. Both now start under the
 * labels and the notes column stops the same distance inside the right border.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("Patient Encounter Worksheet print config unit tests")
@Tag("unit")
@Tag("fast")
class PatientEncounterWorksheetPrintConfigUnitTest extends CarlosUnitTestBase {

    private static final String CONFIG = "/oscar/form/prop/patientEncounterWorksheetCfg.txt";
    private static final double TEMPLATE_LEFT_BORDER = 30.25;
    private static final double TEMPLATE_RIGHT_BORDER = 581.75;
    private static final double MINIMUM_GAP = 4;

    @Test
    void shouldStartAllergiesColumn_underTheTemplateLabel() throws IOException {
        String[] allergies = field("allergies");

        // Top 215 and leading 10 put the first 8 pt baseline at 225, where the single line always printed.
        assertThat(String.join(",", Arrays.copyOf(allergies, 10))).isEqualTo("left,36,215,0,BaseFont.HELVETICA,8,_,576,233,10");
        assertThat(Double.parseDouble(allergies[1]) - TEMPLATE_LEFT_BORDER).isGreaterThanOrEqualTo(MINIMUM_GAP);
        assertThat(TEMPLATE_RIGHT_BORDER - Double.parseDouble(allergies[7])).isGreaterThanOrEqualTo(MINIMUM_GAP);
        assertThat(allergies).contains("continue=Allergies (continued)", "fallback=7/8/214");
    }

    @Test
    void shouldKeepEncounterNotesColumn_insideBothBorders() throws IOException {
        String[] notes = field("encounter_notes");

        assertThat(String.join(",", Arrays.copyOf(notes, 10))).isEqualTo("left,36,261,0,BaseFont.HELVETICA,8,_,576,630,10");
        assertThat(Double.parseDouble(notes[1]) - TEMPLATE_LEFT_BORDER).isGreaterThanOrEqualTo(MINIMUM_GAP);
        assertThat(TEMPLATE_RIGHT_BORDER - Double.parseDouble(notes[7])).isGreaterThanOrEqualTo(MINIMUM_GAP);
        assertThat(notes).contains("continue=Encounter Notes (continued)",
                "marker=\u2026 (continued on page {page})",
                "patient=Patient Encounter Worksheet   {demo_name}   Pat ID: {demo_id}   DoB: {demo_bday}");
    }

    @Test
    void shouldOptInOnlyTheWorksheet_amongShippedPrintConfigs() throws Exception {
        // ColumnContinuation changes how a form prints only when a config line opts in; every other
        // form must keep printing exactly as before.
        Path dir = Path.of(PatientEncounterWorksheetPrintConfigUnitTest.class.getResource("/oscar/form/prop").toURI());
        List<String> optedIn = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".txt")).sorted().toList()) {
                Properties config = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    config.load(in);
                }
                for (String name : config.stringPropertyNames()) {
                    String[] values = config.getProperty(name).split(",");
                    if (values.length > 10) {
                        optedIn.add(file.getFileName() + ":" + name + ":" + values[10].trim().startsWith(ColumnContinuation.OPT_IN));
                    }
                }
            }
        }
        assertThat(optedIn).containsExactlyInAnyOrder(
                "patientEncounterWorksheetCfg.txt:allergies:true", "patientEncounterWorksheetCfg.txt:encounter_notes:true");
    }

    /** Reads one field the way FrmPDFServlet does: a Properties value split on commas, each part trimmed. */
    private static String[] field(String name) throws IOException {
        Properties config = new Properties();
        try (InputStream in = PatientEncounterWorksheetPrintConfigUnitTest.class.getResourceAsStream(CONFIG)) {
            assertThat(in).as(CONFIG).isNotNull();
            config.load(in);
        }
        String value = config.getProperty(name);
        assertThat(value).as(name).isNotNull();
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
