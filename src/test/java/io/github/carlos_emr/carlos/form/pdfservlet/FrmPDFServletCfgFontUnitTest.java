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

import java.awt.Color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.BaseFont;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link FrmPDFServlet#cfgFont} reads the font value of a print-config line. Every value a
 * shipped print config uses is listed, including the misspelt {@code BaseFone.HELVETICA} and
 * {@code BaseFont.TIMES_ROMAN}, which have always printed in Courier.
 *
 * @since 2026-10-15
 */
@DisplayName("FrmPDFServlet print-config font unit tests")
@Tag("unit")
@Tag("fast")
class FrmPDFServletCfgFontUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "BaseFont.HELVETICA          | Helvetica         | Cp1252       | 0",
            "BaseFont.HELVETICA;italic   | Helvetica         | Cp1252       | 2",
            "BaseFont.HELVETICA;bold     | Helvetica         | Cp1252       | 1",
            "BaseFont.HELVETICA;bolditalic | Helvetica       | Cp1252       | 3",
            "BaseFont.HELVETICA;other    | Helvetica         | Cp1252       | 0",
            "BaseFont.HELVETICA_OBLIQUE  | Helvetica-Oblique | Cp1252       | 0",
            "BaseFont.ZAPFDINGBATS       | ZapfDingbats      | ZapfDingbats | 0",
            "BaseFont.COURIER            | Courier           | Cp1252       | 0",
            "BaseFone.HELVETICA          | Courier           | Cp1252       | 0",
            "BaseFont.TIMES_ROMAN        | Courier           | Cp1252       | 0",
    })
    void shouldReadFontValue_asTheFormPrintAlwaysHas(String value, String font, String encoding, int flags)
            throws Exception {
        FrmPDFServlet.CfgFont cfgFont = FrmPDFServlet.cfgFont(value);

        BaseFont expected = PdfFonts.createFont(font, encoding, BaseFont.NOT_EMBEDDED);
        assertThat(cfgFont.baseFont().getPostscriptFontName()).isEqualTo(expected.getPostscriptFontName());
        assertThat(cfgFont.flags()).isEqualTo(flags);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"BaseFont.HELVETICA;bold, true", "BaseFont.HELVETICA, false", "BaseFont.HELVETICA;italic, false"})
    void shouldPrintBoldColumnsBlack_onlyWhenBold(String value, boolean black) throws Exception {
        Font font = FrmPDFServlet.cfgFont(value).columnFont(9);

        assertThat(font.getSize()).isEqualTo(9);
        assertThat(Color.BLACK.equals(font.getColor())).isEqualTo(black);
    }
}
