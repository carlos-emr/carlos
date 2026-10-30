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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_CONVERTED;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.SAFE_MODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link HtmlPdfRenderResult}: defensive copies of the PDF bytes and the
 * degradation set, content-based equality (a record's default would compare array identity), and
 * a {@code toString()} that prints a size and enum names, never the document.
 *
 * @since 2026-10-29
 */
@DisplayName("HtmlPdfRenderResult")
@Tag("unit")
@Tag("htmlpdf")
class HtmlPdfRenderResultUnitTest {

    @Test
    void shouldCopyBytes_onConstructionAndAccess() {
        byte[] input = {1, 2, 3};
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(input, Set.of());

        input[0] = 99;
        byte[] returned = result.pdf();
        returned[1] = 99;

        assertThat(result.pdf()).containsExactly(1, 2, 3);
        assertThat(result.pdf()).isNotSameAs(result.pdf());
    }

    @Test
    void shouldCompareContents_forEquality() {
        HtmlPdfRenderResult first = new HtmlPdfRenderResult(
                new byte[] {1, 2, 3}, EnumSet.of(IMAGE_CONVERTED, SAFE_MODE));
        HtmlPdfRenderResult second = new HtmlPdfRenderResult(
                new byte[] {1, 2, 3}, new LinkedHashSet<>(Arrays.asList(SAFE_MODE, IMAGE_CONVERTED)));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isEqualTo(first);

        assertThat(first).as("different bytes").isNotEqualTo(new HtmlPdfRenderResult(
                new byte[] {1, 2, 4}, EnumSet.of(IMAGE_CONVERTED, SAFE_MODE)));
        assertThat(first).as("different degradations").isNotEqualTo(new HtmlPdfRenderResult(
                new byte[] {1, 2, 3}, EnumSet.of(IMAGE_CONVERTED)));
        assertThat(first).as("null").isNotEqualTo(null);
        assertThat(first).as("another type").isNotEqualTo("HtmlPdfRenderResult");
    }

    @Test
    void shouldPrintSizeAndNamesOnly_fromToString() {
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(
                new byte[] {1, 2, 3}, EnumSet.of(IMAGE_CONVERTED));

        assertThat(result.toString()).contains("size=3", "IMAGE_CONVERTED").doesNotContain("[B@");
        // The exact format also rules out the bytes appearing as numbers (Arrays.toString).
        assertThat(result.toString()).isEqualTo("HtmlPdfRenderResult[size=3, degradations=[IMAGE_CONVERTED]]");

        // The bytes are a patient's document: their content must never reach a log line.
        HtmlPdfRenderResult document = new HtmlPdfRenderResult(
                "FAKE-Patient".getBytes(StandardCharsets.US_ASCII), Set.of());
        assertThat(document.toString()).isEqualTo("HtmlPdfRenderResult[size=12, degradations=[]]");
    }

    @Test
    void shouldListDegradationsInDeclarationOrder_fromToString() {
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(
                new byte[0], new LinkedHashSet<>(Arrays.asList(SAFE_MODE, IMAGE_UNAVAILABLE, IMAGE_CONVERTED)));

        assertThat(result.toString()).contains("[IMAGE_CONVERTED, IMAGE_UNAVAILABLE, SAFE_MODE]");
    }

    @Test
    void shouldExposeUnmodifiableDegradations_forResult() {
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(new byte[0], EnumSet.of(IMAGE_CONVERTED));

        assertThat(result.degradations()).containsExactly(IMAGE_CONVERTED);
        assertThatThrownBy(() -> result.degradations().add(SAFE_MODE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.degradations().remove(IMAGE_CONVERTED))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.degradations().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCopyDegradations_onConstruction() {
        Set<HtmlPdfRenderDegradation> source = EnumSet.of(IMAGE_CONVERTED);
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(new byte[0], source);

        source.add(SAFE_MODE);
        source.remove(IMAGE_CONVERTED);

        assertThat(result.degradations()).containsExactly(IMAGE_CONVERTED);
    }

    @Test
    void shouldAcceptAnySetImplementation_forDegradations() {
        // EnumSet.copyOf(...) throws on an empty collection that is not itself an EnumSet, and an
        // immutable Set.of() is exactly what a render without degradations passes around.
        assertThat(new HtmlPdfRenderResult(new byte[0], Set.of()).degradations()).isEmpty();
        assertThat(new HtmlPdfRenderResult(new byte[0], new HashSet<>()).degradations()).isEmpty();
        assertThat(new HtmlPdfRenderResult(new byte[0], Set.of(SAFE_MODE)).degradations())
                .containsExactly(SAFE_MODE);
    }

    @Test
    void shouldRejectNulls_forConstructor() {
        Set<HtmlPdfRenderDegradation> withNullEntry = new HashSet<>(Arrays.asList(SAFE_MODE, null));

        assertThatNullPointerException().as("null bytes")
                .isThrownBy(() -> new HtmlPdfRenderResult(null, Set.of()));
        assertThatNullPointerException().as("null set")
                .isThrownBy(() -> new HtmlPdfRenderResult(new byte[0], null));
        assertThatNullPointerException().as("null entry in the set")
                .isThrownBy(() -> new HtmlPdfRenderResult(new byte[0], withNullEntry));
    }

    @Test
    void shouldReportByteCount_fromSize() {
        assertThat(new HtmlPdfRenderResult(new byte[7], Set.of()).size()).isEqualTo(7);
        assertThat(new HtmlPdfRenderResult(new byte[0], Set.of()).size()).isZero();
    }

    @Test
    void shouldWriteWithoutCopy_toOutputStream() throws IOException {
        byte[] input = {10, 20, 30};
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(input, Set.of());
        ArrayCapturingStream first = new ArrayCapturingStream();
        ArrayCapturingStream second = new ArrayCapturingStream();

        result.writeTo(first);
        result.writeTo(second);

        assertThat(first.written()).containsExactly(10, 20, 30);
        assertThat(second.written()).containsExactly(10, 20, 30);
        // A copy per call would hand each stream a different array; the constructor's own copy
        // is the only array the result ever holds.
        assertThat(first.seenArray).as("the result's own array on every call").isSameAs(second.seenArray);
        assertThat(first.seenArray).as("not the caller's array").isNotSameAs(input);
        assertThat(first.seenArray).as("not a pdf() copy").isNotSameAs(result.pdf());
    }

    @Test
    void shouldPropagateIoFailure_fromWriteTo() {
        HtmlPdfRenderResult result = new HtmlPdfRenderResult(new byte[] {1}, Set.of());
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        assertThatThrownBy(() -> result.writeTo(failing)).isInstanceOf(IOException.class);
    }

    /** Records the array it is handed, so a test can tell a copy from the result's own bytes. */
    private static final class ArrayCapturingStream extends OutputStream {
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private byte[] seenArray;

        @Override
        public void write(int b) {
            sink.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            seenArray = b;
            sink.write(b, off, len);
        }

        byte[] written() {
            return sink.toByteArray();
        }
    }
}
