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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents full of large pictures must never exhaust the heap. A child JVM is started with the test class
 * path, a 256 MB heap and {@code -XX:+ExitOnOutOfMemoryError} (as production runs), so the Surefire
 * configuration is unchanged and an out-of-memory error ends the child, not the build. The child renders, on
 * four threads, documents with six near-cap PNGs, a 36-megapixel JPEG and a 48-megapixel colour PNG scan, and exits
 * 0 only if every one of them came out as a PDF.
 *
 * <p>Tagged {@code slow}, not {@code unit}: it makes pictures of tens of megapixels and takes a while.</p>
 */
@Tag("slow")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF memory")
class HtmlPdfMemoryUnitTest {

    @TempDir
    Path directory;

    private static void writePng(BufferedImage image, Path target) throws IOException {
        assertThat(ImageIO.write(image, "png", target.toFile())).isTrue();
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.5f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    @Test
    @DisplayName("should produce every document of near-cap, large and scanned pictures on four threads with a 256 MB heap")
    void shouldProduceEveryDocument_withASmallHeap() throws Exception {
        // Near the child's per-image cap (about 1.9 megapixels at 256 MB): a gradient with a little noise.
        for (String name : HtmlPdfMemoryProbe.NEAR_CAP_PNGS) {
            BufferedImage image = TestImages.picture(1500, 1200, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < 2000; i++) {
                image.setRGB((i * 7919) % 1500, (i * 104729) % 1200, (i * 2654435761L) % 16777216 > 0 ? (int) ((i * 2654435761L) % 16777216) : 0);
            }
            writePng(image, directory.resolve(name));
        }
        // A phone-camera-sized JPEG (36 megapixels), embedded as it is, never decoded.
        BufferedImage large = TestImages.picture(6000, 6000, BufferedImage.TYPE_INT_RGB);
        writeJpeg(large, directory.resolve(HtmlPdfMemoryProbe.LARGE_JPEG));
        large = null;
        // A 48-megapixel colour scan, read as a stream with subsampling.
        BufferedImage scan = new BufferedImage(8000, 6000, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[scan.getWidth()];
        for (int y = 0; y < scan.getHeight(); y++) {
            for (int x = 0; x < row.length; x++) {
                row[x] = (x / 16 + y / 16) % 2 == 0 ? 0xF2F2F2 : 0x282846;
            }
            scan.setRGB(0, y, row.length, 1, row, 0, row.length);
        }
        writePng(scan, directory.resolve(HtmlPdfMemoryProbe.LARGE_SCAN));
        scan = null;
        for (String copy : HtmlPdfMemoryProbe.MORE_SCANS) {
            Files.copy(directory.resolve(HtmlPdfMemoryProbe.LARGE_SCAN), directory.resolve(copy));
        }
        assertThat(Files.size(directory.resolve(HtmlPdfMemoryProbe.LARGE_JPEG))).isGreaterThan(100_000);

        Path log = directory.resolve("probe.log");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-cp", classPath,
                HtmlPdfMemoryProbe.class.getName(), directory.toString());
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("LANG", "C.UTF-8");
        builder.environment().put("LC_ALL", "C.UTF-8");
        Process child = builder.start();
        boolean finished = child.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            child.destroyForcibly();
        }
        List<String> output = Files.readAllLines(log);

        assertThat(finished).as("the child JVM finished within 5 minutes").isTrue();
        assertThat(child.exitValue()).as("child exit code; its output:%n%s", String.join("\n", output)).isZero();
        assertThat(output).anyMatch(line -> line.startsWith("all 8 documents were produced"));
        assertThat(new File(directory.toFile(), HtmlPdfMemoryProbe.LARGE_JPEG)).exists();
    }
}
