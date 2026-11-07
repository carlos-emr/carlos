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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A rendered PDF and the steps down the render took to produce it.
 *
 * <p>A result always holds a complete PDF: when nothing printable can be produced the service
 * throws instead of returning partial bytes. An empty {@code degradations} set means the document
 * printed as authored.</p>
 *
 * <p>The record is immutable. The constructor copies the bytes and the set, {@link #pdf()} returns
 * a copy, and {@link #size()} and {@link #writeTo(OutputStream)} read the bytes without copying.
 * {@code equals} and {@code hashCode} compare the PDF's contents (a record's defaults would compare
 * array identity). The PDF is a patient's document, so {@link #toString()} prints only its size
 * and the degradation names, never an array reference or any content.</p>
 *
 * @param pdf the PDF bytes; copied on construction and on every {@link #pdf()} call
 * @param degradations every step down the render took; stored as an unmodifiable set that iterates
 *        in {@link HtmlPdfRenderDegradation} declaration order
 * @since 2026-10-29
 */
public record HtmlPdfRenderResult(byte[] pdf, Set<HtmlPdfRenderDegradation> degradations) {

    /**
     * Copies the PDF bytes and the degradation set.
     *
     * @throws NullPointerException if {@code pdf}, {@code degradations} or an element of the set
     *         is {@code null}
     */
    public HtmlPdfRenderResult {
        Objects.requireNonNull(pdf, "pdf");
        Objects.requireNonNull(degradations, "degradations");
        pdf = pdf.clone();
        // noneOf + addAll, not EnumSet.copyOf: copyOf throws on an empty collection that is not
        // itself an EnumSet, such as the Set.of() a render without degradations passes in.
        Set<HtmlPdfRenderDegradation> copy = EnumSet.noneOf(HtmlPdfRenderDegradation.class);
        copy.addAll(degradations);
        degradations = Collections.unmodifiableSet(copy);
    }

    /**
     * {@return a copy of the PDF bytes; changing it does not change this result} Use
     * {@link #size()} and {@link #writeTo(OutputStream)} to avoid the copy.
     */
    @Override
    public byte[] pdf() {
        return pdf.clone();
    }

    /**
     * {@return the length of the PDF in bytes, without copying it}
     */
    public int size() {
        return pdf.length;
    }

    /**
     * Writes the PDF to {@code out} without copying it. The stream is handed this result's own
     * array, so it must not modify or keep it.
     *
     * @param out where to write the PDF; not closed by this method
     * @throws IOException if {@code out} fails
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(pdf);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof HtmlPdfRenderResult that
                        && Arrays.equals(pdf, that.pdf)
                        && degradations.equals(that.degradations));
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(pdf) + degradations.hashCode();
    }

    /**
     * Prints the PDF's size and the degradation names only.
     */
    @Override
    public String toString() {
        return "HtmlPdfRenderResult[size=" + pdf.length + ", degradations=" + degradations + "]";
    }
}
