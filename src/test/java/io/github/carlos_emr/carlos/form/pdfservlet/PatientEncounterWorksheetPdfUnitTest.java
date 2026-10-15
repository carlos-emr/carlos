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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.servlet.ServletContext;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the Patient Encounter Worksheet through {@link FrmPDFServlet} with its real template and
 * print config, and checks that long allergies and notes are printed in full.
 *
 * <p>The allergies box holds one 8 pt line under its label. A list that does not fit prints at 7 pt
 * on two lines, and anything still left continues on an extra page, as do notes longer than their
 * box. Page 1 then says where the text continues, and every page counts the extra pages.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("Patient Encounter Worksheet PDF unit tests")
@Tag("unit")
@Tag("fast")
class PatientEncounterWorksheetPdfUnitTest extends CarlosUnitTestBase {

    private static final String SHORT_ALLERGIES = "FAKE Penicillin,FAKE Latex";
    private static final String END_OF_NOTES = "FAKE_END_OF_NOTES";
    private static final String END_OF_ALLERGIES = "FAKE_LAST_ALLERGY";

    @Test
    void shouldPrintShortTextOnOnePage_withoutContinuation() throws Exception {
        try (PDDocument pdf = render(SHORT_ALLERGIES, "FAKE short note.")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String page1 = text(pdf, 1);
            assertThat(page1).contains("Page 1 of 1").contains("FAKE short note.").doesNotContain("continued");
        }
    }

    @Test
    void shouldKeepShortAllergies_onOneEightPointLineUnderTheLabel() throws Exception {
        try (PDDocument pdf = render(SHORT_ALLERGIES, "FAKE short note.")) {
            List<Line> allergies = allergyLines(pdf);
            assertThat(allergies).hasSize(1);
            assertThat(allergies.get(0).text()).isEqualTo(SHORT_ALLERGIES);
            assertThat(allergies.get(0).y()).isCloseTo(225, within());
            assertThat(allergies.get(0).x0()).isCloseTo(36, within());
            assertThat(allergies.get(0).size()).isCloseTo(8, within());
        }
    }

    @Test
    void shouldWrapLongAllergies_onTwoSevenPointLinesInsideTheBox() throws Exception {
        String allergies = allergyList(14) + "," + END_OF_ALLERGIES;
        try (PDDocument pdf = render(allergies, "FAKE short note.")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            List<Line> lines = allergyLines(pdf);
            assertThat(lines).hasSize(2);
            assertThat(lines).allSatisfy(line -> {
                assertThat(line.size()).isCloseTo(7, within());
                assertThat(line.x0()).isCloseTo(36, within());
                assertThat(line.x1()).isLessThanOrEqualTo(576);
            });
            assertThat(lines.get(0).y()).isCloseTo(222, within());
            assertThat(lines.get(1).y()).isCloseTo(230, within());
            assertThat(boxText(pdf, 215, 233.5)).isEqualTo(allergies);
        }
    }

    @Test
    void shouldContinueNotesTooLongForTheBox_onAnExtraPage() throws Exception {
        try (PDDocument pdf = render(SHORT_ALLERGIES, notes(90))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            String page1 = text(pdf, 1);
            String page2 = text(pdf, 2);
            assertThat(page1).contains("Page 1 of 2").contains("(continued on page 2)").doesNotContain(END_OF_NOTES);
            assertThat(page2).contains("Page 2 of 2")
                    .contains("Encounter Notes (continued)")
                    .contains("FAKE-Worksheet, Robin (F)").contains("Pat ID: 3001").contains("DoB: 1985-06-15 (41)")
                    .contains(END_OF_NOTES);
            assertThat(boxText(pdf, 258, 632) + " " + continued(pdf, "Encounter Notes (continued)"))
                    .isEqualTo(normalize(notes(90)));
        }
    }

    @Test
    void shouldContinueVeryLongNotes_overAsManyPagesAsNeeded() throws Exception {
        try (PDDocument pdf = render(SHORT_ALLERGIES, notes(400))) {
            int pages = pdf.getNumberOfPages();
            assertThat(pages).isGreaterThan(2);
            assertThat(text(pdf, 1)).contains("Page 1 of " + pages);
            assertThat(text(pdf, pages)).contains("Page " + pages + " of " + pages).contains(END_OF_NOTES);
            assertThat(boxText(pdf, 258, 632) + " " + continued(pdf, "Encounter Notes (continued)"))
                    .isEqualTo(normalize(notes(400)));
        }
    }

    @Test
    void shouldContinueAllergiesTooLongForTwoLines_beforeTheNotes() throws Exception {
        String allergies = allergyList(60) + "," + END_OF_ALLERGIES;
        try (PDDocument pdf = render(allergies, notes(90))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            String page1 = text(pdf, 1);
            String page2 = text(pdf, 2);
            assertThat(page1).doesNotContain(END_OF_ALLERGIES);
            assertThat(allergyLines(pdf)).hasSize(2).last()
                    .satisfies(line -> assertThat(line.text()).endsWith("(continued on page 2)"));
            assertThat(page2.indexOf("Allergies (continued)")).isGreaterThanOrEqualTo(0)
                    .isLessThan(page2.indexOf("Encounter Notes (continued)"));
            assertThat(page2).contains(END_OF_ALLERGIES).contains(END_OF_NOTES);
            assertThat(boxText(pdf, 215, 233.5) + " " + continued(pdf, "Allergies (continued)")).isEqualTo(allergies);
            assertThat(boxText(pdf, 258, 632) + " " + continued(pdf, "Encounter Notes (continued)"))
                    .isEqualTo(normalize(notes(90)));
        }
    }

    private static PDDocument render(String allergies, String notes) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/createpdf");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setLastName("FAKE-Doctor");
        provider.setFirstName("Pat");
        loggedInInfo.setLoggedInProvider(provider);
        request.getSession().setAttribute(loggedInInfo.LOGGED_IN_INFO_KEY, loggedInInfo);
        Map<String, String> params = Map.of(
                "demographic_no", "3001", "form_id", "0",
                "__title", "PatientEcounterWorksheet",
                "__cfgfile", "patientEncounterWorksheetCfg",
                "__template", "patientEncounterWorksheet",
                "demo_name", "FAKE-Worksheet, Robin (F)", "demo_id", "3001", "demo_bday", "1985-06-15 (41)",
                "allergies", allergies, "encounter_notes", notes);
        params.forEach(request::setParameter);
        ByteArrayOutputStream bytes = new ClasspathFrmPDFServlet()
                .generatePDFDocumentBytes(request, null, new ByteArrayOutputStream(), 0);
        return Loader.loadPDF(bytes.toByteArray());
    }

    private static String allergyList(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> String.format("FAKE Allergen %02d", n))
                .collect(Collectors.joining(","));
    }

    private static String notes(int sentences) {
        return IntStream.rangeClosed(1, sentences)
                .mapToObj(n -> "FAKE note sentence " + n + " for the worksheet continuation check.")
                .collect(Collectors.joining(" ")) + " " + END_OF_NOTES;
    }

    private static String text(PDDocument pdf, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Page 1 text inside one box (y between the two values), with the continuation marker taken off. */
    private static String boxText(PDDocument pdf, double fromY, double toY) throws IOException {
        return normalize(lines(pdf, 1).stream().filter(line -> line.y() > fromY && line.y() < toY)
                .map(Line::text).collect(Collectors.joining(" "))
                .replaceAll("\\s*\u2026\\s*\\(continued on page \\d+\\)$", ""));
    }

    /** The text under one continuation heading, over every extra page, without the page headers. */
    private static String continued(PDDocument pdf, String heading) throws IOException {
        List<String> body = new ArrayList<>();
        boolean inSection = false;
        for (int page = 2; page <= pdf.getNumberOfPages(); page++) {
            for (Line line : lines(pdf, page)) {
                if (line.y() < 50) {
                    continue; // printed-on line and patient line
                }
                if (line.text().endsWith("(continued)")) {
                    inSection = line.text().equals(heading);
                } else if (inSection) {
                    body.add(line.text());
                }
            }
        }
        return normalize(String.join(" ", body));
    }

    /** Text lines inside the allergies box: below its label, above its bottom border. */
    private static List<Line> allergyLines(PDDocument pdf) throws IOException {
        return lines(pdf, 1).stream().filter(line -> line.y() > 215 && line.y() < 233.5).toList();
    }

    private static List<Line> lines(PDDocument pdf, int page) throws IOException {
        List<Line> lines = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                TextPosition first = positions.get(0);
                TextPosition last = positions.get(positions.size() - 1);
                Line line = new Line(text, first.getYDirAdj(), first.getXDirAdj(),
                        last.getXDirAdj() + last.getWidthDirAdj(), first.getFontSizeInPt());
                Line previous = lines.isEmpty() ? null : lines.get(lines.size() - 1);
                if (previous != null && Math.abs(previous.y() - line.y()) < 0.5) {
                    // A right-aligned marker is a separate run on the same baseline: one printed line.
                    lines.set(lines.size() - 1, new Line(previous.text() + " " + line.text(), previous.y(),
                            previous.x0(), line.x1(), previous.size()));
                } else {
                    lines.add(line);
                }
            }
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.writeText(pdf, new StringWriter());
        return lines;
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.6);
    }

    /** Serves the webapp's {@code /WEB-INF/classes/} print configs from the test classpath, as Tomcat would. */
    private static final class ClasspathFrmPDFServlet extends FrmPDFServlet {
        private final MockServletContext context = new MockServletContext() {
            @Override
            public InputStream getResourceAsStream(String path) {
                String prefix = "/WEB-INF/classes/";
                return path.startsWith(prefix)
                        ? FrmPDFServlet.class.getResourceAsStream("/" + path.substring(prefix.length()))
                        : null;
            }
        };

        @Override
        public ServletContext getServletContext() {
            return context;
        }
    }

    private record Line(String text, double y, double x0, double x1, double size) {
    }
}
