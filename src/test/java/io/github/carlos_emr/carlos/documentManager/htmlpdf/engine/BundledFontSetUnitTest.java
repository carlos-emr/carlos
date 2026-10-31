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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet.Face;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link BundledFontSet}: which faces load and which characters each can draw.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("pdf")
@DisplayName("BundledFontSet Unit Tests")
class BundledFontSetUnitTest {

    /** The shared copy of the fonts, so the parsed fonts OpenPDF caches by path are not multiplied by the tests. */
    private final Path shared = TestFonts.sharedDirectory();
    private final Path sans = shared.resolve("DejaVuSans.ttf");
    private final Path sansBold = shared.resolve("DejaVuSans-Bold.ttf");
    private final Path cjk = shared.resolve("wqy-microhei.ttc");
    private final Path symbol = shared.resolve("Symbola_hint.ttf");

    @TempDir
    Path directory;

    @Test
    @DisplayName("should report which characters each face can draw")
    void shouldReportCoverage_forBundledFaces() throws PDFGenerationException {
        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, cjk, symbol);

        assertThat(fonts.canDraw(Face.SANS_REGULAR, 'é')).isTrue();
        assertThat(fonts.canDraw(Face.SANS_BOLD, 'é')).isTrue();
        assertThat(fonts.canDraw(Face.SANS_REGULAR, '中')).isFalse();
        assertThat(fonts.canDraw(Face.CJK, '中')).isTrue();
        assertThat(fonts.canDraw(Face.SANS_REGULAR, 0x1D11E)).isFalse();
        assertThat(fonts.canDraw(Face.SYMBOL, 0x1D11E)).isTrue();
    }

    @Test
    @DisplayName("should report every face available when all four files load")
    void shouldReportAllFacesAvailable_whenAllFilesLoad() throws PDFGenerationException {
        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, cjk, symbol);

        for (Face face : Face.values()) {
            assertThat(fonts.isAvailable(face)).as(face.name()).isTrue();
        }
    }

    @Test
    @DisplayName("should mark a fallback unavailable when its path is null")
    void shouldMarkFallbackUnavailable_whenPathNull() throws PDFGenerationException {
        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, null, symbol);

        assertThat(fonts.isAvailable(Face.CJK)).isFalse();
        assertThat(fonts.canDraw(Face.CJK, '中')).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> fonts.fontPath(Face.CJK))
                .withMessageContaining("CJK");
        assertThat(fonts.isAvailable(Face.SYMBOL)).isTrue();
    }

    @Test
    @DisplayName("should leave a fallback out when its file is not a font")
    void shouldLeaveFallbackOut_whenFileIsNotAFont() throws PDFGenerationException, IOException {
        Path notAFont = Files.writeString(directory.resolve("not-a-font.ttf"), "not a font", StandardCharsets.UTF_8);

        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, notAFont, directory.resolve("absent.ttf"));

        assertThat(fonts.isAvailable(Face.CJK)).isFalse();
        assertThat(fonts.isAvailable(Face.SYMBOL)).isFalse();
        assertThat(fonts.isAvailable(Face.SANS_REGULAR)).isTrue();
    }

    @Test
    @DisplayName("should throw when a DejaVu Sans face is missing")
    void shouldThrow_whenSansFaceMissing() {
        Path missing = directory.resolve("no-such-font-file.ttf");

        assertThatExceptionOfType(PDFGenerationException.class)
                .isThrownBy(() -> BundledFontSet.load(missing, sansBold, null, null))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("no-such-font-file")
                        .doesNotContain(directory.toString()));
        assertThatExceptionOfType(PDFGenerationException.class)
                .isThrownBy(() -> BundledFontSet.load(sans, missing, null, null));
    }

    @Test
    @DisplayName("should throw when a DejaVu Sans face is not a font")
    void shouldThrow_whenSansFaceIsNotAFont() throws IOException {
        Path notAFont = Files.writeString(directory.resolve("not-a-font.ttf"), "not a font", StandardCharsets.UTF_8);

        assertThatExceptionOfType(PDFGenerationException.class)
                .isThrownBy(() -> BundledFontSet.load(notAFont, sansBold, null, null))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(directory.toString()));
    }

    @Test
    @DisplayName("should throw when a DejaVu Sans path is null")
    void shouldThrow_whenSansPathNull() {
        assertThatExceptionOfType(PDFGenerationException.class)
                .isThrownBy(() -> BundledFontSet.load(null, sansBold, null, null));
        assertThatExceptionOfType(PDFGenerationException.class)
                .isThrownBy(() -> BundledFontSet.load(sans, null, null, null));
    }

    @Test
    @DisplayName("should address the Chinese font collection by its first font")
    void shouldAddressCollectionByIndexZero_forFontPath() throws PDFGenerationException {
        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, cjk, symbol);

        assertThat(fonts.fontPath(Face.CJK)).isEqualTo(cjk + ",0");
        assertThat(fonts.fontPath(Face.SANS_REGULAR)).isEqualTo(sans.toString());
        assertThat(fonts.fontPath(Face.SYMBOL)).isEqualTo(symbol.toString());
    }

    @Test
    @DisplayName("should answer false for coverage of an unavailable face")
    void shouldAnswerFalse_whenFaceUnavailableForCoverage() throws PDFGenerationException {
        BundledFontSet fonts = BundledFontSet.load(sans, sansBold, null, null);

        assertThat(fonts.canDraw(Face.SYMBOL, 'a')).isFalse();
    }
}
