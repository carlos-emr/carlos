/**
 * Copyright (c) 2024-2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.printing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.BaseFont;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FontSettings} — the shared font configuration used
 * by all PDF generators in CARLOS EMR.
 *
 * <p>Validates that pre-defined constants and the four-arg constructor produce
 * correct OpenPDF {@link BaseFont} configurations.</p>
 *
 * @since 2026-03-04
 */
@Tag("unit")
@Tag("pdf")
@DisplayName("FontSettings Unit Tests")
class FontSettingsUnitTest {

    /** Tests for pre-defined constant FontSettings instances. */
    @Nested
    @DisplayName("Pre-defined Constants")
    class PreDefinedConstants {

        @Test
        @DisplayName("should have font size 6 for HELVETICA_6PT")
        void shouldHaveFontSize6_forHelvetica6pt() {
            assertThat(FontSettings.HELVETICA_6PT.getFontSize()).isEqualTo(6);
        }

        @Test
        @DisplayName("should have font size 10 for HELVETICA_10PT")
        void shouldHaveFontSize10_forHelvetica10pt() {
            assertThat(FontSettings.HELVETICA_10PT.getFontSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("should have font size 12 for HELVETICA_12PT")
        void shouldHaveFontSize12_forHelvetica12pt() {
            assertThat(FontSettings.HELVETICA_12PT.getFontSize()).isEqualTo(12);
        }

        @Test
        @DisplayName("should use Helvetica font for all constants")
        void shouldUseHelveticaFont_forAllConstants() {
            assertThat(FontSettings.HELVETICA_6PT.getFont()).isEqualTo(BaseFont.HELVETICA);
            assertThat(FontSettings.HELVETICA_10PT.getFont()).isEqualTo(BaseFont.HELVETICA);
            assertThat(FontSettings.HELVETICA_12PT.getFont()).isEqualTo(BaseFont.HELVETICA);
        }

        @Test
        @DisplayName("should use WinAnsi code page for all constants")
        void shouldUseWinAnsiCodePage_forAllConstants() {
            assertThat(FontSettings.HELVETICA_6PT.getCodePage()).isEqualTo(BaseFont.WINANSI);
            assertThat(FontSettings.HELVETICA_10PT.getCodePage()).isEqualTo(BaseFont.WINANSI);
            assertThat(FontSettings.HELVETICA_12PT.getCodePage()).isEqualTo(BaseFont.WINANSI);
        }

        @Test
        @DisplayName("should not be embedded for all constants")
        void shouldNotBeEmbedded_forAllConstants() {
            assertThat(FontSettings.HELVETICA_6PT.isEmbedded()).isFalse();
            assertThat(FontSettings.HELVETICA_10PT.isEmbedded()).isFalse();
            assertThat(FontSettings.HELVETICA_12PT.isEmbedded()).isFalse();
        }
    }

    /** Tests for the four-arg constructor. */
    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should store custom font settings")
        void shouldStoreCustomFontSettings() {
            FontSettings custom = new FontSettings(
                    BaseFont.TIMES_ROMAN, BaseFont.CP1252, true, 14);
            assertThat(custom.getFont()).isEqualTo(BaseFont.TIMES_ROMAN);
            assertThat(custom.getCodePage()).isEqualTo(BaseFont.CP1252);
            assertThat(custom.isEmbedded()).isTrue();
            assertThat(custom.getFontSize()).isEqualTo(14);
        }
    }
}
