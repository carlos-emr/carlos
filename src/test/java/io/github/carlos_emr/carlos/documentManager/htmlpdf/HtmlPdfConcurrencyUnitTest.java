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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The beans are shared by every render and keep nothing of one, so a render must come out the same whatever
 * else is rendering. Twenty-four mixed documents on eight threads are compared, page by page, with the same
 * documents rendered one at a time: same page count, same text, same pixels.
 *
 * <p>The PDF bytes themselves differ from run to run (creation date and file identifier), so the comparison is
 * of what a reader sees.</p>
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF concurrency")
class HtmlPdfConcurrencyUnitTest {

    private static final List<String> FIXTURES = List.of("annual/female.html", "annual/male.html", "forms/all-controls.html",
            "hrm/demo-text-as-html.html", "hrm/table-heavy.html", "email/body.html");
    private static final int RENDERS = 24;
    private static final int THREADS = 8;

    @TempDir
    Path directory;

    /** What a reader sees of a PDF: page count, then for every page its text and a digest of its pixels. */
    private static String appearance(HtmlPdfRenderResult result) throws Exception {
        byte[] pdf = result.pdf();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<String> texts = TestPdfs.pageTexts(pdf);
        for (int page = 1; page <= texts.size(); page++) {
            BufferedImage image = TestPdfs.image(pdf, page);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            ByteBuffer bytes = ByteBuffer.allocate(pixels.length * 4);
            bytes.asIntBuffer().put(pixels);
            digest.update(bytes.array());
            digest.update(texts.get(page - 1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return texts.size() + ":" + HexFormat.of().formatHex(digest.digest()) + ":" + result.degradations();
    }

    @Test
    @DisplayName("should give the same output and no error for 24 mixed renders on 8 threads as for the same documents one at a time")
    void shouldGiveIdenticalOutput_underConcurrency() throws Exception {
        Path webRoot = HtmlPdfFixtures.webRoot(directory);
        HtmlPdfRenderService service = HtmlPdfTestServices.newService();
        List<String> expected = new ArrayList<>();
        for (String fixture : FIXTURES) {
            expected.add(appearance(HtmlPdfFixtures.render(service, fixture, webRoot)));
        }
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<String>> renders = new ArrayList<>();
            for (int i = 0; i < RENDERS; i++) {
                int which = i % FIXTURES.size();
                Callable<String> render = () -> appearance(HtmlPdfFixtures.render(service, FIXTURES.get(which), webRoot));
                renders.add(executor.submit(render));
            }

            for (int i = 0; i < RENDERS; i++) {
                assertThat(renders.get(i).get(5, TimeUnit.MINUTES)).as("render %d of %s", i, FIXTURES.get(i % FIXTURES.size()))
                        .isEqualTo(expected.get(i % FIXTURES.size()));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
