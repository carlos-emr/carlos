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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderException.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link HtmlPdfRenderException}: it must carry where an attempt failed and what
 * failed, and no text of the document.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HtmlPdfRenderException Unit Tests")
class HtmlPdfRenderExceptionUnitTest {

    @Test
    @DisplayName("should copy the stack trace but not the message, and chain no cause")
    void shouldCopyStackWithoutMessage_inRenderException() {
        IllegalStateException original = new IllegalStateException("FAKE-Patient Name, 1980-01-01");

        HtmlPdfRenderException exception = new HtmlPdfRenderException(PrintMode.NORMAL, Phase.LAYOUT_PASS_1, original);

        assertThat(exception.getMessage()).doesNotContain("FAKE-").doesNotContain("1980");
        assertThat(exception.getMessage()).contains("NORMAL", "LAYOUT_PASS_1", "java.lang.IllegalStateException");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getSuppressed()).isEmpty();
        assertThat(exception.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(exception.phase()).isEqualTo(Phase.LAYOUT_PASS_1);
        assertThat(exception.mode()).isEqualTo(PrintMode.NORMAL);
    }

    @Test
    @DisplayName("should not leak the message of the original through its cause chain either")
    void shouldNotLeakMessage_throughCauseChain() {
        Exception original = new Exception("outer FAKE-secret", new RuntimeException("inner FAKE-secret"));
        original.addSuppressed(new RuntimeException("suppressed FAKE-secret"));

        HtmlPdfRenderException exception = new HtmlPdfRenderException(PrintMode.TEXT_ONLY, Phase.PLACE, original);

        StringBuilder everything = new StringBuilder();
        for (Throwable t = exception; t != null; t = t.getCause()) {
            everything.append(t);
        }
        assertThat(everything.toString()).doesNotContain("FAKE-secret");
    }

    @Test
    @DisplayName("should name an error class, not only exceptions")
    void shouldAcceptErrors_asOriginal() {
        HtmlPdfRenderException exception =
                new HtmlPdfRenderException(PrintMode.SAFE, Phase.IMAGES, new NoClassDefFoundError("FAKE-class"));

        assertThat(exception.getMessage()).contains("java.lang.NoClassDefFoundError").doesNotContain("FAKE-class");
    }

    @Test
    @DisplayName("should reject missing arguments")
    void shouldRejectNull_whenArgumentMissing() {
        Exception original = new Exception();

        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderException(null, Phase.PLACE, original));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderException(PrintMode.NORMAL, null, original));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderException(PrintMode.NORMAL, Phase.PLACE, null));
    }

    @Test
    @DisplayName("should have the phases the pipeline reports")
    void shouldDefinePhases_ofTheAttempt() {
        assertThat(Phase.values()).extracting(Enum::name).containsExactly("PREPARE", "IMAGES", "LAYOUT_PASS_1",
                "LAYOUT_PASS_2", "PLACE", "VALIDATE");
    }
}
