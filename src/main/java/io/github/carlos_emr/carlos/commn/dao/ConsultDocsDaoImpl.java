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

import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.Document;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ConsultDocsDaoImpl extends AbstractDaoImpl<ConsultDocs> implements ConsultDocsDao {
    private static final String DEMOGRAPHIC_MODULE = "demographic";

    private static final String STALE_ACTIVE_CONSULT_ATTACHMENTS_QUERY =
            "SELECT cd FROM ConsultDocs cd "
                    + "WHERE cd.deleted IS NULL "
                    + "AND EXISTS (SELECT cr.id FROM ConsultationRequest cr WHERE cr.id = cd.requestId) "
                    + "AND ("
                    + "(cd.docType = :eformType AND ("
                    + "NOT EXISTS (SELECT e.id FROM EFormData e WHERE e.id = cd.documentNo) "
                    + "OR EXISTS (SELECT e.id FROM EFormData e, ConsultationRequest cr "
                    + "WHERE e.id = cd.documentNo AND cr.id = cd.requestId AND (e.patientIndependent IS NULL OR e.patientIndependent = false) "
                    + "AND (e.demographicId IS NULL OR e.demographicId <> cr.demographicId))"
                    + ")) "
                    + "OR "
                    + "(cd.docType = :documentType AND ("
                    + "NOT EXISTS (SELECT d.documentNo FROM Document d WHERE d.documentNo = cd.documentNo) "
                    + "OR EXISTS (SELECT d.documentNo FROM Document d "
                    + "WHERE d.documentNo = cd.documentNo AND d.status = :deletedDocumentStatus) "
                    + "OR NOT EXISTS (SELECT ctl.id.documentNo FROM CtlDocument ctl, Document d, ConsultationRequest cr "
                    + "WHERE ctl.id.documentNo = cd.documentNo AND d.documentNo = cd.documentNo AND cr.id = cd.requestId "
                    + "AND d.status = ctl.status AND d.status <> :deletedDocumentStatus "
                    + "AND ctl.id.module = :demographicModule AND ctl.id.moduleId = cr.demographicId)"
                    + "))"
                    + ")";

    public ConsultDocsDaoImpl() {
        super(ConsultDocs.class);
    }

    public List<ConsultDocs> findByRequestIdDocNoDocType(Integer requestId, Integer documentNo, String docType) {
        String sql = "select x from ConsultDocs x where x.requestId=?1 and x.documentNo=?2 and x.docType=?3 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, requestId);
        query.setParameter(2, documentNo);
        query.setParameter(3, docType);

        List<ConsultDocs> results = query.getResultList();
        return results;
    }

    public List<ConsultDocs> findByRequestIdDocType(Integer requestId, String docType) {
        String sql = "select x from ConsultDocs x where x.requestId=?1 and x.docType=?2 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, requestId);
        query.setParameter(2, docType);

        List<ConsultDocs> results = query.getResultList();
        if (results == null) {
            return Collections.emptyList();
        }
        return results;
    }

    public List<ConsultDocs> findByRequestId(Integer requestId) {
        String sql = "select x from ConsultDocs x where x.requestId=?1 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, requestId);

        List<ConsultDocs> results = query.getResultList();
        return results;
    }

    public List<Object[]> findLabs(Integer consultationId) {
        Query q = entityManager.createQuery("SELECT cd, plr FROM ConsultDocs cd, PatientLabRouting plr WHERE plr.labNo = cd.documentNo AND cd.requestId = :consultationId AND cd.docType = :docType AND cd.deleted IS NULL ORDER BY cd.documentNo");
        q.setParameter("consultationId", consultationId);
        q.setParameter("docType", ConsultDocs.DOCTYPE_LAB);
        return q.getResultList();
    }

    public List<ConsultDocs> findStaleActiveConsultAttachments() {
        Query query = createStaleActiveConsultAttachmentsQuery();
        return query.getResultList();
    }

    public int countStaleActiveConsultAttachments() {
        return findStaleActiveConsultAttachments().size();
    }

    public int markStaleActiveConsultAttachmentsDeleted() {
        List<ConsultDocs> consultDocs = findStaleActiveConsultAttachments();
        for (ConsultDocs consultDoc : consultDocs) {
            consultDoc.setDeleted(ConsultDocs.DELETED);
            entityManager.merge(consultDoc);
        }
        return consultDocs.size();
    }

    private Query createStaleActiveConsultAttachmentsQuery() {
        Query query = entityManager.createQuery(STALE_ACTIVE_CONSULT_ATTACHMENTS_QUERY);
        query.setParameter("eformType", ConsultDocs.DOCTYPE_EFORM);
        query.setParameter("documentType", ConsultDocs.DOCTYPE_DOC);
        query.setParameter("deletedDocumentStatus", Document.STATUS_DELETED);
        query.setParameter("demographicModule", DEMOGRAPHIC_MODULE);
        return query;
    }
}
