/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.model.Document;

import java.util.function.UnaryOperator;

/**
 * Fits a document description that CARLOS builds itself to {@code document.docdesc}.
 *
 * <p>The column is a utf8mb4 {@code VARCHAR(255)} ({@link Document#DESCRIPTION_MAX_LENGTH}), which
 * counts Unicode code points, and a packaged install's MariaDB is not in strict mode, so a longer
 * value is cut off at its end without an error. Text a person typed is never shortened: the
 * actions that take it refuse an over-long description with a message instead (see
 * {@link Document#descriptionFits(String)}). A description CARLOS composes itself (a file name
 * used when none was given, an annotated copy's " (annotated)" suffix, an eForm template, an
 * imported title, an API caller's text) is shortened here, before it is stored, so that:</p>
 * <ul>
 *   <li>the cut is counted as the column counts it, in code points, and never falls inside a
 *       surrogate pair (an emoji is never split into a broken character);</li>
 *   <li>the part that identifies the document survives: a file name keeps its extension and a
 *       suffix such as " (annotated)" is kept whole, the text before it being shortened;</li>
 *   <li>a blank left at the cut is dropped, as {@link EDoc} trims the description it stores.</li>
 * </ul>
 */
public final class DocumentDescription {

    /** The longest extension, dot included, a shortened file name keeps whole (".pdf", ".docx", ".jpeg"). */
    static final int MAX_KEPT_EXTENSION = 16;

    private DocumentDescription() {
    }

    /**
     * The description itself when it fits the column, otherwise its first code points up to the
     * column's length, without a blank left at the cut.
     *
     * @param description a description CARLOS built, or {@code null}
     * @return {@code description} when it fits (the same instance), {@code null} for {@code null}
     */
    public static String fit(String description) {
        if (Document.descriptionFits(description)) {
            return description;
        }
        return firstCodePoints(description, Document.DESCRIPTION_MAX_LENGTH).stripTrailing();
    }

    /**
     * {@code base + suffix}, with {@code base} shortened so that the whole suffix fits the column.
     *
     * @param base   the descriptive part, which may be shortened; {@code null} counts as empty
     * @param suffix the part that must survive whole; {@code null} counts as empty
     * @return a description of at most {@link Document#DESCRIPTION_MAX_LENGTH} code points that ends
     *         with {@code suffix} (only a suffix that alone fills the column is itself cut)
     */
    public static String fitWithSuffix(String base, String suffix) {
        String head = base == null ? "" : base;
        String tail = suffix == null ? "" : suffix;
        String whole = head + tail;
        if (Document.descriptionFits(whole)) {
            return whole;
        }
        int room = Document.DESCRIPTION_MAX_LENGTH - tail.codePointCount(0, tail.length());
        if (room <= 0) {
            // The suffix alone fills the column: keep as much of it as fits rather than the base.
            return fit(tail);
        }
        return firstCodePoints(head, room).stripTrailing() + tail;
    }

    /**
     * A file name used as a description, shortened to the column with its extension kept, so that a
     * shortened name still says what kind of file it is. A name without a short extension is cut
     * at its end like any other description.
     *
     * @param fileName the stored file name, or {@code null}
     * @return the name itself when it fits, otherwise a name of at most
     *         {@link Document#DESCRIPTION_MAX_LENGTH} code points
     */
    public static String fitFileName(String fileName) {
        if (Document.descriptionFits(fileName)) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String extension = fileName.substring(dot);
            if (extension.codePointCount(0, extension.length()) <= MAX_KEPT_EXTENSION) {
                return fitWithSuffix(fileName.substring(0, dot), extension);
            }
        }
        return fit(fileName);
    }

    /**
     * The stored form of a description that is encoded before it is stored (the OTN eConsult import
     * stores it HTML-encoded): the encoding of the longest leading part of {@code raw} whose
     * encoding fits the column. Shortening the text before encoding never leaves part of an escape
     * (such as {@code &am}) at the end, as cutting the encoded value would.
     *
     * @param raw     the description before encoding, or {@code null}
     * @param encoder the encoding applied before storage; a longer input never encodes shorter
     * @return {@code encoder.apply(raw)} when it fits, otherwise the fitting encoded prefix
     */
    public static String fitEncoded(String raw, UnaryOperator<String> encoder) {
        String encoded = encoder.apply(raw);
        if (raw == null || Document.descriptionFits(encoded)) {
            return encoded;
        }
        // The encoded length grows with the number of leading code points taken, so search for the
        // longest prefix that still fits.
        int fits = 0;
        int tooLong = raw.codePointCount(0, raw.length());
        while (tooLong - fits > 1) {
            int middle = (fits + tooLong) >>> 1;
            if (Document.descriptionFits(encoder.apply(firstCodePoints(raw, middle)))) {
                fits = middle;
            } else {
                tooLong = middle;
            }
        }
        return encoder.apply(firstCodePoints(raw, fits).stripTrailing());
    }

    private static String firstCodePoints(String value, int count) {
        return value.substring(0, value.offsetByCodePoints(0, count));
    }
}
