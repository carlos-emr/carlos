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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The page of every form control, printed: what each control becomes on paper, what never prints (hidden
 * fields, buttons, the unselected options), and that the text survives the steps a form PDF goes through
 * afterwards (merging for a fax, flattening for Messenger).
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF forms")
class HtmlPdfFormsUnitTest {

    private static final HtmlPdfRenderService SERVICE = HtmlPdfTestServices.newService();

    @TempDir
    Path directory;

    private HtmlPdfRenderResult result;
    private String text;
    private String flat;

    @BeforeEach
    void render() throws Exception {
        result = HtmlPdfFixtures.render(SERVICE, "forms/all-controls.html", HtmlPdfFixtures.webRoot(directory));
        text = String.join("\n", TestPdfs.pageTexts(result.pdf()));
        flat = text.replaceAll("\\s+", " ");
    }

    private float leftOf(String phrase) throws IOException {
        List<TextPosition> glyphs = TestPdfs.glyphs(result.pdf());
        StringBuilder all = new StringBuilder();
        List<Integer> glyphOfChar = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            String unicode = glyphs.get(i).getUnicode();
            for (int c = 0; c < unicode.length(); c++) {
                glyphOfChar.add(i);
            }
            all.append(unicode);
        }
        int at = all.indexOf(phrase);
        assertThat(at).as("phrase %s", phrase).isGreaterThanOrEqualTo(0);
        return glyphs.get(glyphOfChar.get(at)).getXDirAdj();
    }

    @Test
    @DisplayName("should print every value, Chinese and Hebrew and accented ones included")
    void shouldPrintEveryValue_inEveryScript() {
        assertThat(flat).contains("FAKE-Patient Name", "张伟", "FAKE-中文", "שלום", "עברית",
                "évaluation à la façon de Zürich ≥ 5", "2026-10-29", "42", "fake.patient@example.invalid",
                "FAKE-readonly value", "FAKE-disabled value");
    }

    @Test
    @DisplayName("should print a ticked checkbox as a crossed box, an empty one as an empty box, and a radio as a filled or an empty circle")
    void shouldPrintCheckAndRadioMarks_asBoxesAndCircles() {
        assertThat(flat).contains("☒ checked one", "☐ unchecked two", "☒ checked three", "☐ unchecked four");
        assertThat(flat).contains("○ option A", "◉ option B", "○ option C");
    }

    @Test
    @DisplayName("should never print a hidden field, its name or its value")
    void shouldNeverPrint_hiddenFields() {
        assertThat(text).doesNotContain("HIDDEN", "demographic_no", "provider_no", "form_class", "formCreated", "4711", "999998");
    }

    @Test
    @DisplayName("should print only the selected option of a list, the first when none is selected, and every chosen one of a multiple list")
    void shouldPrintOnlySelectedOptions_ofLists() {
        assertThat(flat).contains("FAKE-second selected", "one-b chosen", "FAKE-red, FAKE-blue", "FAKE-default one");
        assertThat(flat).doesNotContain("FAKE-first", "FAKE-third", "one-a", "two-a", "FAKE-Group", "FAKE-green", "FAKE-grey", "FAKE-other two");
    }

    @Test
    @DisplayName("should keep the two lines of a text area, and print a password as eight bullets without its length or value")
    void shouldPrintTextAreaLines_andPasswordBullets() throws Exception {
        assertThat(text).contains("FAKE-line one of the note", "FAKE-line two of the note");
        assertThat(text).doesNotContain("FAKE-secret123");
        assertThat(text).contains("•".repeat(8)).doesNotContain("•".repeat(9));
        // The second line is printed below the first.
        List<TextPosition> glyphs = TestPdfs.glyphs(result.pdf());
        assertThat(glyphs).isNotEmpty();
        assertThat(leftOf("FAKE-line one")).isCloseTo(leftOf("FAKE-line two"), within(1f));
    }

    @Test
    @DisplayName("should not print buttons, image buttons or their labels")
    void shouldNotPrintButtons_orTheirLabels() {
        assertThat(flat).doesNotContain("FAKE-Submit", "FAKE-Button", "FAKE-Reset", "FAKE-image button");
    }

    @Test
    @DisplayName("should draw progress and meter as bars filled to their value, with no text of their own")
    void shouldDrawProgressAndMeterAsBars_filledToTheirValue() throws Exception {
        // The fixture's progress is 30 of 100 and its meter 0.6 of 1; the second progress has no value at all.
        assertThat(flat).doesNotContain("30/100", "unknown", "60%");
        BufferedImage page = TestPdfs.image(result.pdf(), 1);

        // The progress fill is #2f6fbf, the meter fill #3f9d3f (the green of a meter with no low, high or optimum)
        // and what is left of either track #e8e8e8.
        BarRow progress = BarRow.widestFill(page, 0x2F6FBF);
        BarRow meter = BarRow.widestFill(page, 0x3F9D3F);

        assertThat(progress.filledShare(page)).isCloseTo(0.30, within(0.05));
        assertThat(meter.filledShare(page)).isCloseTo(0.60, within(0.05));
        // The track of the progress with no value is empty: the fill colour is on the page in one run, not two.
        assertThat(progress.runsOfFill(page)).isEqualTo(1);
        // A meter is half the width of a progress bar, as in a browser.
        assertThat((double) meter.trackWidth(page) / progress.trackWidth(page)).isCloseTo(0.5, within(0.06));
    }

    @Test
    @DisplayName("should wrap a long value inside its box and keep it left-aligned inside a right-aligned cell")
    void shouldLeftAlignLongValue_inRightAlignedCell() throws Exception {
        assertThat(flat).contains("FAKE-long-value-long-value-long-value");
        assertThat(leftOf("FAKE-long-value")).isCloseTo(leftOf("FAKE-Patient Name"), within(8f));
    }

    @Test
    @DisplayName("should keep the text identical after the merge the fax uses, and after the merge and a flattening pass")
    void shouldKeepText_afterMergeAndFlatten() throws Exception {
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        int skipped = ConcatPDF.concat(List.of(new ByteArrayInputStream(result.pdf())), merged);
        ByteArrayOutputStream flattened = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(new PdfReader(merged.toByteArray()), flattened);
        stamper.setFormFlattening(true);
        stamper.close();

        assertThat(skipped).isZero();
        assertThat(String.join("\n", TestPdfs.pageTexts(merged.toByteArray()))).isEqualTo(text);
        assertThat(String.join("\n", TestPdfs.pageTexts(flattened.toByteArray()))).isEqualTo(text);
    }
    /**
     * One bar read off a rendered page: the row where its fill (a colour) is widest, and the fill's start and length
     * in that row. The unfilled rest of a bar is the track colour {@code #e8e8e8}.
     */
    private record BarRow(int row, int start, int length, int fill) {

        private static final int TRACK = 0xE8E8E8;

        static BarRow widestFill(BufferedImage page, int fill) {
            BarRow widest = new BarRow(-1, -1, 0, fill);
            for (int y = 0; y < page.getHeight(); y++) {
                int x = 0;
                while (x < page.getWidth()) {
                    if (!near(page.getRGB(x, y), fill)) {
                        x++;
                        continue;
                    }
                    int end = x;
                    while (end < page.getWidth() && near(page.getRGB(end, y), fill)) {
                        end++;
                    }
                    if (end - x > widest.length) {
                        widest = new BarRow(y, x, end - x, fill);
                    }
                    x = end;
                }
            }
            assertThat(widest.length).as("a bar filled with #%06x", fill).isGreaterThan(0);
            return widest;
        }

        /** How many separate runs of the fill colour the row holds. */
        int runsOfFill(BufferedImage page) {
            int runs = 0;
            boolean inRun = false;
            for (int x = 0; x < page.getWidth(); x++) {
                boolean on = near(page.getRGB(x, row), fill);
                if (on && !inRun) {
                    runs++;
                }
                inRun = on;
            }
            return runs;
        }

        /** The width of the fill and the track colour after it, past at most two pixels where the two blend. */
        int trackWidth(BufferedImage page) {
            int x = start + length;
            int blended = 0;
            while (blended < 2 && x < page.getWidth() && !near(page.getRGB(x, row), TRACK)) {
                x++;
                blended++;
            }
            while (x < page.getWidth() && near(page.getRGB(x, row), TRACK)) {
                x++;
            }
            return x - start;
        }

        double filledShare(BufferedImage page) {
            return (double) length / trackWidth(page);
        }

        private static boolean near(int rgb, int colour) {
            return Math.abs(((rgb >> 16) & 0xFF) - ((colour >> 16) & 0xFF)) <= 12
                    && Math.abs(((rgb >> 8) & 0xFF) - ((colour >> 8) & 0xFF)) <= 12
                    && Math.abs((rgb & 0xFF) - (colour & 0xFF)) <= 12;
        }
    }
}
