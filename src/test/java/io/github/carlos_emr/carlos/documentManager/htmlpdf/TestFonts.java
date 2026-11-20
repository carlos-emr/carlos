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

    /**
     * Why tests share one extracted copy of the fonts: OpenPDF caches every {@code BaseFont} it
     * creates by file path for the life of the JVM, and a parsed copy of the four bundled fonts costs
     * about 25 MB. A test that extracts the fonts into its own temporary directory for each test
     * method leaves a copy behind each time, and a few dozen of them exhaust a test JVM's heap. In
     * production the path is stable (one directory per font bundle), so the cache does not grow.
     */
    private static final class Shared {
        static final Path DIRECTORY = createDirectory();
        static final BundledFontSet FONTS = load(DIRECTORY);

        private static Path createDirectory() {
            try {
                Path directory = Files.createTempDirectory("carlos-test-fonts-");
                for (String resource : new String[]{SANS_REGULAR, SANS_BOLD, CJK, SYMBOL}) {
                    copy(resource, directory);
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(directory)));
                return directory;
            } catch (IOException e) {
                throw new IllegalStateException("The test fonts could not be extracted", e);
            }
        }

        private static BundledFontSet load(Path directory) {
            try {
                return BundledFontSet.load(directory.resolve("DejaVuSans.ttf"), directory.resolve("DejaVuSans-Bold.ttf"),
                        directory.resolve("wqy-microhei.ttc"), directory.resolve("Symbola_hint.ttf"));
            } catch (PDFGenerationException e) {
                throw new IllegalStateException("The test fonts could not be loaded", e);
            }
        }

        private static void deleteQuietly(Path directory) {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
                Files.deleteIfExists(directory);
            } catch (IOException e) {
                // A temporary directory left behind is harmless.
            }
        }
    }

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
     * The directory holding the four fonts, extracted once per JVM. Use it, not a new directory, as
     * the font directory of any render in a test.
     *
     * @return the directory ({@code DejaVuSans.ttf}, {@code DejaVuSans-Bold.ttf},
     *         {@code wqy-microhei.ttc}, {@code Symbola_hint.ttf})
     */
    public static Path sharedDirectory() {
        return Shared.DIRECTORY;
    }

    /**
     * The four fonts loaded from {@link #sharedDirectory()}, once per JVM.
     *
     * @return the set
     */
    public static BundledFontSet sharedFonts() {
        return Shared.FONTS;
    }

    /**
     * Forgets the fonts OpenPDF has cached by path. A test that must create fonts under many
     * different paths (the font-file service writes under a new directory per test) calls this
     * afterwards so the copies do not pile up; the shared fonts are parsed again when next used.
     */
    public static void clearOpenPdfFontCache() {
        try {
            java.lang.reflect.Field field = org.openpdf.text.pdf.BaseFont.class.getDeclaredField("fontCache");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            // The cache is an optimisation of OpenPDF's; failing to clear it only costs memory.
        }
    }

    /**
     * Copies all four fonts into a directory and loads them. Prefer {@link #sharedFonts()}; this
     * copy is for tests that must own the files.
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
