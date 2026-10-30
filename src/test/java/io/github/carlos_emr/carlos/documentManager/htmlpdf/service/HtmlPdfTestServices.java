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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;

import java.nio.file.Path;

/**
 * Test helper: a real {@link HtmlPdfRenderService} over the real collaborators, for tests in other
 * packages (the legacy facades' tests) that cannot name the package-private service classes it is built
 * from. It lives in the module's own package for that reason. It uses the fonts every test shares
 * ({@link TestFonts#sharedFonts()}) instead of extracting its own copy: OpenPDF caches a parsed copy of the
 * fonts by file path for the life of the JVM, and a copy per test would exhaust a test JVM's heap.
 */
public final class HtmlPdfTestServices {

    private HtmlPdfTestServices() {
    }

    /**
     * Builds a render service with production limits (two slots, a 10 s wait) over the real document rules,
     * image step, layout and output checks.
     *
     * @return the service
     */
    public static HtmlPdfRenderService newService() {
        return new HtmlPdfRenderService(new PrintDocumentComposer(), new PrintImageService(), new PrintLayoutService(),
                new RenderedPdfValidator(), sharedFontFiles());
    }

    /**
     * Builds a render service that sends HTML longer than a given number of characters straight to
     * {@code TEXT_ONLY}, with everything else as in production. The size-limit measurement of
     * {@code HtmlPdfComparisonMain} raises it to see how long a normal-mode layout takes.
     *
     * @param textOnlyHtmlChars the longest HTML, in characters, that is laid out as authored
     * @return the service
     */
    public static HtmlPdfRenderService newService(int textOnlyHtmlChars) {
        return new HtmlPdfRenderService(new PrintDocumentComposer(), new PrintImageService(), new PrintLayoutService(),
                new RenderedPdfValidator(), sharedFontFiles(), HtmlPdfRenderService.SLOT_WAIT,
                Runtime.getRuntime().maxMemory() / HtmlPdfRenderService.HEAP_DIVISOR_FOR_IMAGES / HtmlPdfRenderService.RENDER_SLOTS,
                textOnlyHtmlChars, Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * The font-file service of the tests: it hands out the shared fonts without writing any file.
     *
     * @return the service
     */
    static BundledFontFileService sharedFontFiles() {
        return new BundledFontFileService() {
            @Override
            BundledFontSet fontSet() {
                return TestFonts.sharedFonts();
            }
        };
    }
}
