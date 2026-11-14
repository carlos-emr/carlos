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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a patient's chart shows for one document, decided from its captured {@code document}
 * row and its {@code ctl_document} patient link exactly as CARLOS stores them.
 *
 * <p>{@code EDocUtil.deleteDocument} only sets {@code document.status} to {@code D} and keeps
 * the link (so it can be undeleted); a link whose own status is {@code D} is a deleted
 * association. Neither is shown in the chart, so neither is exported, the same rule as
 * {@code CtlDocumentDao.findDocumentNosForDemographic} and {@code AttachmentSelectionAccess}
 * (a NULL link status is a live legacy link). The legacy exporter exported active documents
 * only.
 *
 * <p>An Add HTML or Add Link document ({@code AddEditHtml2Action}) has status {@code H}, a
 * file name such as {@code 20260305101500html} that names no file, and its content in
 * {@code document.docxml}. {@code ManageDocument2Action.display} renders a non-blank
 * {@code docxml} as HTML instead of any file, so a document whose {@code docxml} is not empty
 * is represented by that stored HTML and its file is never read.
 *
 * <p>The capture reads a file exactly for {@link #FILE}; the {@code DOCUMENT_FILES} read plan
 * in {@code CdsExportPatientTableReadSupport} applies the same rule in SQL, and the occurrence
 * service refuses a capture in which the two disagree.
 */
enum CdsExportDocumentRepresentation {
    /** A live uploaded or scanned document (status A, no stored HTML): its stored file. */
    FILE,
    /** A live document whose content is the HTML kept in {@code docxml}. */
    STORED_HTML,
    /** A soft-deleted document or a deleted patient link: not in the chart, not exported. */
    REMOVED,
    /**
     * A document the chart cannot display (an HTML document with no stored HTML, which the
     * chart reports as a missing file) or with a status this release does not know.
     */
    UNREPRESENTED;

    /** The disposition of every captured field of a document the chart no longer shows. */
    static final String REMOVED_RULE = "DELETED_DOCUMENT_OR_LINK_NOT_SHOWN_IN_CHART";

    static CdsExportDocumentRepresentation of(
            Map<String, String> document, Map<String, String> link) {
        String status = document.get("status");
        if (removed(status, link.get("status"))) return REMOVED;
        String html = document.get("docxml");
        if (html != null && !html.isEmpty())
            return "A".equals(status) || "H".equals(status) ? STORED_HTML : UNREPRESENTED;
        return "A".equals(status) ? FILE : UNREPRESENTED;
    }

    static boolean removed(String documentStatus, String linkStatus) {
        return "D".equals(documentStatus) || "D".equals(linkStatus);
    }

    /**
     * The same removal decision from an authenticated document or link row witness, without
     * reading clinical values: the patient inventory must not expect a removed document.
     */
    static boolean removed(CdsExportNativeRowReceiptDto witness) throws IOException {
        if (witness.state() == null)
            throw new IOException("CDS_EXPORT_DOCUMENT_STATUS_WITNESS_REQUIRED");
        return CdsExportImmunizationProjectionEvidence.matches(witness.state(), "status", "D");
    }

    /** One explicit not-exported disposition for every captured field of a removed document. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> removedDispositions(
            CdsExportCapturedTextRowDto document, CdsExportCapturedTextRowDto link) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : List.of(document, link)) {
            var fields = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet()) fields.operational(column, REMOVED_RULE);
        }
        return result;
    }
}
