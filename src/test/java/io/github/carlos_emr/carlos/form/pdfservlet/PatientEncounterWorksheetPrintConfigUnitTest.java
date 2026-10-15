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
import java.util.Properties;

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
    void shouldStartAllergiesLine_underTheTemplateLabel() throws IOException {
        String[] allergies = field("allergies");

        assertThat(String.join(",", allergies)).isEqualTo("left,36,225,0,BaseFont.HELVETICA,8");
        assertThat(Double.parseDouble(allergies[1]) - TEMPLATE_LEFT_BORDER).isGreaterThanOrEqualTo(MINIMUM_GAP);
    }

    @Test
    void shouldKeepEncounterNotesColumn_insideBothBorders() throws IOException {
        String[] notes = field("encounter_notes");

        assertThat(String.join(",", notes)).isEqualTo("left,36,261,0,BaseFont.HELVETICA,8,_,576,630,10");
        assertThat(Double.parseDouble(notes[1]) - TEMPLATE_LEFT_BORDER).isGreaterThanOrEqualTo(MINIMUM_GAP);
        assertThat(TEMPLATE_RIGHT_BORDER - Double.parseDouble(notes[7])).isGreaterThanOrEqualTo(MINIMUM_GAP);
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
