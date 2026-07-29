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
package io.github.carlos_emr.carlos.eform.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the delete-safety invariant of {@link EFormBrowserPdfService.RenderedEformPdf}: because
 * {@code close()} deletes the wrapped file, the compact constructor must reject any path that is not
 * this renderer's own {@code eform-browser-render-*.pdf} output, so the AutoCloseable can never be
 * turned into an arbitrary-file delete. Without these tests, widening the filename check would
 * silently convert it into a delete primitive over the shared temp root.
 */
@Tag("unit")
@DisplayName("RenderedEformPdf delete-safety guard")
class RenderedEformPdfUnitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should reject a null path")
    void shouldReject_nullPath() {
        assertThatThrownBy(() -> new EFormBrowserPdfService.RenderedEformPdf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should reject a path that is not renderer output")
    void shouldReject_nonRendererPath() {
        assertThatThrownBy(() -> new EFormBrowserPdfService.RenderedEformPdf(Path.of("/etc/passwd")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a renderer-prefixed name that is not a .pdf")
    void shouldReject_rendererPrefixButNotPdf() {
        assertThatThrownBy(() ->
                new EFormBrowserPdfService.RenderedEformPdf(tempDir.resolve("eform-browser-render-abc.txt")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should accept renderer output and delete it on close")
    void shouldAcceptAndDeleteOnClose_forRendererOutput() throws Exception {
        Path pdf = tempDir.resolve("eform-browser-render-abc.pdf");
        Files.write(pdf, new byte[] {1, 2, 3});
        assertThat(Files.exists(pdf)).isTrue();

        try (EFormBrowserPdfService.RenderedEformPdf rendered =
                     new EFormBrowserPdfService.RenderedEformPdf(pdf)) {
            assertThat(rendered.path()).isEqualTo(pdf);
        }

        // close() deleted the wrapped renderer output.
        assertThat(Files.exists(pdf)).isFalse();
    }
}
