/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.MyDateFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Optimistic baselines exclude review state so concurrent reviews of unchanged content can coexist. */
public final class DocumentEditRevision {
    private DocumentEditRevision() { }

    /**
     * Computes the current revision from stored HTML or the bytes of the managed document file.
     *
     * @param document the persisted document
     * @return the SHA-256 revision for its current content
     * @throws IOException if the stored file cannot be read
     * @throws SecurityException if the stored filename is unsafe
     */
    public static String content(Document document) throws IOException {
        if (document.getStatus() == 'H') return digest(document.getDocxml());
        return StoredDocumentRevision.forDocumentFile(document.getDocfilename());
    }

    /**
     * Hashes the stored metadata editable by the document editor, including dates at day precision.
     *
     * @param d the persisted document
     * @return the optimistic metadata revision
     */
    public static String metadata(Document d) {
        return digest(d.getDoctype(), d.getDocClass(), d.getDocSubClass(), d.getDocdesc(), d.getSource(),
                d.getSourceFacility(), d.getResponsible(), d.getPublic1(), d.isAbnormal(),
                day(d.getReceivedDate()), day(d.getObservationdate()));
    }

    /**
     * Hashes submitted editor metadata in the same format as the persisted revision. The HTML
     * editor uses the stored values for fields it does not submit.
     *
     * @param d the submitted document
     * @param htmlEditor whether the HTML editor submitted the edit
     * @param stored the persisted document used for fields absent from the HTML editor
     * @return the revision representing the submitted metadata
     * @throws NumberFormatException if a nonblank public flag is not an integer
     */
    public static String metadata(EDoc d, boolean htmlEditor, Document stored) {
        return digest(d.getType(), d.getDocClass(), d.getDocSubClass(), d.getDescription(), d.getSource(),
                d.getSourceFacility(), d.getResponsibleId(), number(d.getDocPublic()),
                htmlEditor ? stored.isAbnormal() : Boolean.parseBoolean(d.getAbnormal()),
                htmlEditor ? day(stored.getReceivedDate()) : day(MyDateFormat.getSysDate(d.getReceivedDate())),
                day(MyDateFormat.getSysDate(d.getObservationDate())));
    }

    private static int number(String value) { return value == null || value.isBlank() ? 0 : Integer.parseInt(value); }
    private static String day(Date date) { return date == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(date); }

    /**
     * Hashes the length-prefixed UTF-8 string form of each field in the supplied order.
     *
     * @param fields the revision fields, with null fields encoded as empty strings
     * @return the lowercase hexadecimal SHA-256 digest
     */
    public static String digest(Object... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object field : fields) {
                byte[] bytes = Objects.toString(field, "").getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
    }
}
