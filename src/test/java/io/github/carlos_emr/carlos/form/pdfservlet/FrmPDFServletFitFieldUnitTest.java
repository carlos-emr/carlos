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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;

/**
 * A printed form's value is set small enough to stay on its blank when its print-config line says {@code fit}.
 *
 * <p>Mental Health Act Form 14 printed the consenting physician's name from x=415 on the "examination by" line, at a
 * fixed 10pt: a name of more than about twenty characters ran past the blank and off the right edge of the paper,
 * so the signed consent showed a cut name. A config line ending {@code fit, <far edge>} now has the value set in the
 * largest size, down to {@link FrmPDFServlet#MIN_FIT_FONT_SIZE}, at which it ends before that edge.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("form")
@DisplayName("FrmPDFServlet fit fields")
class FrmPDFServletFitFieldUnitTest {

    /** Letter width, the page of every Mental Health Act form template. */
    private static final float PAGE_WIDTH = 612f;

    private static BaseFont helvetica;

    @BeforeAll
    static void loadFont() throws Exception {
        helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
    }

    @Test
    @DisplayName("should keep the configured size for a value that already fits")
    void shouldKeepConfiguredSize_whenValueFits() {
        assertThat(FrmPDFServlet.fittedFontSize(helvetica, "Dr. Lee", 10f, 179f)).isEqualTo(10f);
    }

    @Test
    @DisplayName("should shrink a long value until it is exactly as wide as its blank")
    void shouldShrinkToBlankWidth_whenValueIsTooLong() {
        String name = "FAKE-PW0123456789abcdef BP 120/80 & HR 72; pt's c/o SOB";

        float size = FrmPDFServlet.fittedFontSize(helvetica, name, 10f, 179f);

        assertThat(size).isLessThan(10f).isGreaterThanOrEqualTo(FrmPDFServlet.MIN_FIT_FONT_SIZE);
        assertThat(helvetica.getWidthPoint(name, size)).isCloseTo(179f, within(0.01f));
    }

    @Test
    @DisplayName("should never shrink below the minimum size")
    void shouldStopAtMinimumSize_whenValueCannotFit() {
        String wide = "W".repeat(60);

        assertThat(FrmPDFServlet.fittedFontSize(helvetica, wide, 10f, 179f)).isEqualTo(FrmPDFServlet.MIN_FIT_FONT_SIZE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("should keep the configured size for a blank value")
    void shouldKeepConfiguredSize_whenValueIsBlank(String value) {
        assertThat(FrmPDFServlet.fittedFontSize(helvetica, value, 10f, 179f)).isEqualTo(10f);
    }

    @Test
    @DisplayName("should read a print-config line's alignment, centring anything but left and right")
    void shouldReadAlignment_fromFirstColumn() {
        assertThat(FrmPDFServlet.alignmentOf(" left ")).isEqualTo(PdfContentByte.ALIGN_LEFT);
        assertThat(FrmPDFServlet.alignmentOf("right")).isEqualTo(PdfContentByte.ALIGN_RIGHT);
        assertThat(FrmPDFServlet.alignmentOf("center")).isEqualTo(PdfContentByte.ALIGN_CENTER);
        assertThat(FrmPDFServlet.alignmentOf("anything")).isEqualTo(PdfContentByte.ALIGN_CENTER);
    }

    @Test
    @DisplayName("should keep every fit field of Form 14 on the page")
    void shouldKeepEveryFitField_onForm14Page() throws IOException {
        Properties config = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/oscar/form/prop/mentalHealthForm14Print.txt")) {
            assertThat(in).as("Form 14 print config on the classpath").isNotNull();
            config.load(in);
        }

        List<String> fit = new ArrayList<>();
        for (String field : config.stringPropertyNames()) {
            String[] column = config.getProperty(field).split(",");
            if (column.length == 8 && "fit".equals(column[6].trim())) {
                fit.add(field);
                float x = Float.parseFloat(column[1].trim());
                float edge = Float.parseFloat(column[7].trim());
                assertThat(column[0].trim()).as("%s alignment", field).isEqualTo("left");
                assertThat(edge).as("%s far edge", field).isGreaterThan(x).isLessThanOrEqualTo(PAGE_WIDTH);
            }
        }

        // The fields that hold a name, an address or a facility; the dates and the date of birth are short.
        assertThat(fit).containsExactlyInAnyOrder("name", "address", "physicianName", "nameOfFacility", "clientName",
                "witness", "signature", "relationship");
    }
}
