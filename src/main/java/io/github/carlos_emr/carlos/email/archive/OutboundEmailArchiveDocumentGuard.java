/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.email.archive;

import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import org.apache.commons.io.FilenameUtils;

/**
 * Recognises eDocs that belong to an outbound email archive, so the ordinary document surface
 * can refuse them.
 *
 * <p><strong>Why this exists.</strong> An archived outbound email is stored as a patient eDoc —
 * that is deliberate, it puts the record on the patient's file where it belongs. The side effect
 * is that it becomes reachable through every route that handles ordinary documents: preview,
 * split, re-file, rename, attach to an eForm or consultation, combine, delete. Those routes were
 * written for clinical documents and have no idea some of their inputs are legal records of what
 * was sent to a patient.
 *
 * <p>Editing one corrupts that record. Deleting one destroys it outright, and does so behind the
 * back of {@code OutboundEmailArchiveService.recordControlledDeletion}, which is where deletion
 * is supposed to be gated on {@code _admin.edocdelete}, a released legal hold, a stated reason,
 * and a permanent tombstone. A plain eDoc delete satisfies none of that.
 *
 * <p><strong>Scope.</strong> This is a recognition helper, not an authorization check. It answers
 * "is this an archive artifact?" and nothing else; the caller decides what to do, and callers here
 * uniformly refuse. Access control for legitimate archive reads lives in
 * {@code OutboundEmailArchiveService}, which checks {@code _edoc} and patient access properly.
 *
 * <p><strong>Fails closed on unparseable input.</strong> A request-supplied document id that is
 * not a number cannot identify an archive, so it is not one — the caller's own validation rejects
 * it separately. Returning {@code false} here rather than throwing keeps the guard from turning a
 * malformed-input error into a confusing security error.
 *
 * @since 2026-08-19
 */
public final class OutboundEmailArchiveDocumentGuard {

    /** Standard refusal text for ordinary eDoc workflows that encounter an archive artifact. */
    public static final String REFUSAL_MESSAGE =
            "Outbound email archive eDocs must be managed through the controlled archive workflow";

    private OutboundEmailArchiveDocumentGuard() {
    }

    /**
     * Reports whether a request-supplied document id names an outbound email archive eDoc.
     *
     * @param archiveDao archive DAO to query
     * @param documentId document id as supplied by a request, may be null, blank or non-numeric
     * @return {@code true} only when the id parses and matches an archive artifact or attachment
     */
    public static boolean isArchiveDocument(OutboundEmailArchiveDao archiveDao, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return false;
        }
        try {
            return requireArchiveDao(archiveDao).existsByDocumentNo(Integer.valueOf(documentId.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Reports whether a document number names an outbound email archive eDoc.
     *
     * @param archiveDao archive DAO to query
     * @param documentNo document number, may be null
     * @return {@code true} when it matches an archive artifact or attachment
     */
    public static boolean isArchiveDocument(OutboundEmailArchiveDao archiveDao, Integer documentNo) {
        return documentNo != null && requireArchiveDao(archiveDao).existsByDocumentNo(documentNo);
    }

    /**
     * Reports whether a stored eDoc filename belongs to an outbound email archive.
     *
     * <p>Needed alongside the id checks because parts of the document surface identify a file by
     * name rather than by document number.</p>
     *
     * @param archiveDao archive DAO to query
     * @param fileName stored eDoc filename, may be null or blank
     * @return {@code true} when it matches an archive artifact or attachment
     */
    public static boolean isArchiveFileName(OutboundEmailArchiveDao archiveDao, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        OutboundEmailArchiveDao requiredDao = requireArchiveDao(archiveDao);
        if (requiredDao.existsByFileName(fileName)) {
            return true;
        }

        // Some legacy file APIs sanitize caller input by discarding path components. Guard the
        // effective basename too: otherwise "ignored/<archive-name>" misses the exact lookup and
        // is subsequently normalized onto the protected file in DOCUMENT_DIR.
        String baseName = FilenameUtils.getName(fileName);
        return !baseName.equals(fileName) && requiredDao.existsByFileName(baseName);
    }

    private static OutboundEmailArchiveDao requireArchiveDao(OutboundEmailArchiveDao archiveDao) {
        if (archiveDao == null) {
            throw new IllegalStateException("Outbound email archive DAO is required");
        }
        return archiveDao;
    }
}
