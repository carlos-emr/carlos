/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;

public interface ConsultDocsDao extends AbstractDao<ConsultDocs> {
    List<ConsultDocs> findByRequestIdDocNoDocType(Integer requestId, Integer documentNo, String docType);

    List<ConsultDocs> findByRequestIdDocType(Integer requestId, String docType);

    List<ConsultDocs> findByRequestId(Integer requestId);

    List<Object[]> findLabs(Integer consultationId);

    /**
     * Finds active consultation attachment rows that will be hidden from the
     * renderable attachment lists because the target row is unavailable or does
     * not belong to the consultation demographic.
     *
     * <p>This is runtime reporting only. It covers eForms, documents, and labs
     * because those attachment queries can safely validate existence/ownership.
     * Cleanup remains intentionally narrower and only soft-deletes eForm and
     * document rows.</p>
     *
     * @param requestId consultation request id
     * @return active unavailable consultation attachments for the request
     */
    List<ConsultDocs> findUnavailableActiveConsultAttachments(Integer requestId);

    /**
     * Finds active consultation attachment rows that reference stale eForm or
     * document data that can be validated safely.
     *
     * <p>Only active rows ({@code consultdocs.deleted IS NULL}) with
     * {@code docType='E'} or {@code docType='D'} qualify. eForm rows are stale
     * when the referenced {@code eform_data.fdid} is missing, or when the eForm
     * is not patient-independent and its demographic does not match the
     * consultation request demographic. Document rows are stale when the
     * referenced document is missing, the document is deleted, or no active
     * {@code CtlDocument} demographic link matches the consultation request
     * demographic. Lab, HRM, and form attachments are excluded.</p>
     *
     * @return active stale eForm/document consultation attachments
     */
    List<ConsultDocs> findStaleActiveConsultAttachments();

    /**
     * Counts active stale eForm/document consultation attachments without
     * loading the matching rows.
     *
     * <p>The stale attachment semantics match
     * {@link #findStaleActiveConsultAttachments()}: active E/D rows only,
     * patient-owned eForms/documents must match the consultation demographic,
     * patient-independent eForms remain allowed, and lab/HRM/form rows are
     * excluded.</p>
     *
     * @return number of active stale eForm/document consultation attachments
     */
    int countStaleActiveConsultAttachments();

    /**
     * Soft-deletes active stale eForm/document consultation attachments by
     * setting the existing consult attachment deleted marker.
     *
     * <p>The cleanup applies the same ownership and existence checks as
     * {@link #findStaleActiveConsultAttachments()}. It affects only active E/D
     * rows; valid same-patient eForms/documents, patient-independent eForms,
     * already-deleted rows, labs, HRM attachments, and form attachments are not
     * changed.</p>
     *
     * @return number of active stale attachment rows soft-deleted
     */
    int markStaleActiveConsultAttachmentsDeleted();
}
