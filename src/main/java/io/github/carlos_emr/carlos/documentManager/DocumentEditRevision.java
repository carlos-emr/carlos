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

    public static String content(Document document) throws IOException {
        if (document.getStatus() == 'H') return digest(document.getDocxml());
        return StoredDocumentRevision.forDocumentFile(document.getDocfilename());
    }

    public static String metadata(Document d) {
        return digest(d.getDoctype(), d.getDocClass(), d.getDocSubClass(), d.getDocdesc(), d.getSource(),
                d.getSourceFacility(), d.getResponsible(), d.getPublic1(), d.isAbnormal(),
                day(d.getReceivedDate()), day(d.getObservationdate()));
    }

    public static String metadata(EDoc d, boolean htmlEditor, Document stored) {
        return digest(d.getType(), d.getDocClass(), d.getDocSubClass(), d.getDescription(), d.getSource(),
                d.getSourceFacility(), d.getResponsibleId(), number(d.getDocPublic()),
                htmlEditor ? stored.isAbnormal() : Boolean.parseBoolean(d.getAbnormal()),
                htmlEditor ? day(stored.getReceivedDate()) : day(MyDateFormat.getSysDate(d.getReceivedDate())),
                day(MyDateFormat.getSysDate(d.getObservationDate())));
    }

    private static int number(String value) { return value == null || value.isBlank() ? 0 : Integer.parseInt(value); }
    private static String day(Date date) { return date == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(date); }

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
