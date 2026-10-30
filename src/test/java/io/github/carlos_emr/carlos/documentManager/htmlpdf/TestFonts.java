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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Test helper: the four bundled fonts copied from the class path into a directory and loaded as a
 * {@link BundledFontSet}, without going through {@code BundledFontFileService}.
 */
public final class TestFonts {

    public static final String SANS_REGULAR = "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
    public static final String SANS_BOLD = "net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf";
    public static final String CJK = "fonts/wqy/wqy-microhei.ttc";
    public static final String SYMBOL = "fonts/symbola/Symbola_hint.ttf";

    private TestFonts() {
    }

    /**
     * Copies one bundled font out of the class path.
     *
     * @param resource one of the resource name constants
     * @param directory the directory to copy it into, under its own file name
     * @return the copy
     * @throws IOException if the resource is missing or cannot be copied
     */
    public static Path copy(String resource, Path directory) throws IOException {
        Path target = directory.resolve(resource.substring(resource.lastIndexOf('/') + 1));
        try (InputStream in = TestFonts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("no such bundled font on the class path: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /**
     * Copies all four fonts into a directory and loads them.
     *
     * @param directory an existing directory
     * @return the loaded set
     * @throws IOException if a font cannot be copied
     * @throws PDFGenerationException if a font cannot be loaded
     */
    public static BundledFontSet extractTo(Path directory) throws IOException, PDFGenerationException {
        return BundledFontSet.load(copy(SANS_REGULAR, directory), copy(SANS_BOLD, directory),
                copy(CJK, directory), copy(SYMBOL, directory));
    }
}
