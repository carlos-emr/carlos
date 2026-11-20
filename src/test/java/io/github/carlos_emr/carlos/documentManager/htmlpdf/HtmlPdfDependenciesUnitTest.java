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

import com.ibm.icu.text.Bidi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.extend.FontResolver;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for what the HTML-to-PDF pipeline builds on: the Flying Saucer API it calls and ICU4J
 * must be on the classpath, and no WebP decoder may be.
 *
 * <p>The Flying Saucer and ICU4J checks fail with a plain assertion if a dependency cleanup, a
 * downgrade or a forward merge drops the API, instead of surfacing later as a
 * {@link NoSuchMethodError} deep inside a render. They pin the API the pipeline calls, not the
 * version: the same methods exist in Flying Saucer 10.0.6. The WebP check pins an absence on
 * purpose.</p>
 *
 * @since 2026-10-29
 */
@Tag("unit")
@Tag("htmlpdf")
class HtmlPdfDependenciesUnitTest {

    @Test
    void shouldHaveNoWebpReader_onClasspath() {
        // WebP is deliberately not decoded (ruling R21): the TwelveMonkeys reader could not be
        // bounded from outside (it looped for minutes on corrupt input, ignored subsampling, and
        // allocated from sizes it read out of the data), and WebP never printed before. Putting
        // a decoder back on the classpath, directly or through another library, must be a
        // conscious decision that comes with its own tests, so this fails until it is made.
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isFalse();
    }

    @Test
    void shouldExposeScaleToFit_inFlyingSaucer() throws Exception {
        assertThat(ITextRenderer.class.getMethod("setScaleToFit", boolean.class)).isNotNull();
        // getConstructor only finds public constructors, so this also pins the constructor as API.
        assertThat(ITextRenderer.class.getConstructor(float.class, int.class, ITextOutputDevice.class,
                ITextUserAgent.class, FontResolver.class)).isNotNull();
    }

    @Test
    void shouldReorderHebrew_withIcu4j() {
        // Logical order is alef, bet. In a right-to-left paragraph the visual order is bet, alef,
        // which is the order Flying Saucer must be given because it draws glyphs left to right.
        Bidi bidi = new Bidi();
        bidi.setPara("אב", Bidi.RTL, null);
        assertThat(bidi.writeReordered(Bidi.DO_MIRRORING)).isEqualTo("בא");
    }
}
