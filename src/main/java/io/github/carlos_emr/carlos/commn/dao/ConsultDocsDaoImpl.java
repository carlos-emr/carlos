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

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.LockModeType;
import io.github.carlos_emr.carlos.documentManager.data.LabAttachmentReference;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ConsultDocsDaoImpl extends AbstractDaoImpl<ConsultDocs> implements ConsultDocsDao {

    private final PatientLabRoutingDao patientLabRoutingDao;

    @Autowired
    public ConsultDocsDaoImpl(PatientLabRoutingDao patientLabRoutingDao) {
        super(ConsultDocs.class);
        this.patientLabRoutingDao = patientLabRoutingDao;
    }

    @Override
    public void persist(AbstractModel<?> model) {
        ConsultDocs attachment = (ConsultDocs) model;
        if (ConsultDocs.DOCTYPE_LAB.equals(attachment.getDocType())) {
            var owner = entityManager.find(ConsultationRequest.class,
                    attachment.getRequestId());
            if (owner == null || owner.getDemographicId() == null) {
                throw new IllegalArgumentException("Lab attachment parent is missing");
            }
            String selection = attachment.getLabType() == null ? Integer.toString(attachment.getDocumentNo())
                    : attachment.getLabType() + ":" + attachment.getDocumentNo();
            var reference = LabAttachmentReference.resolve(
                    selection, owner.getDemographicId(), patientLabRoutingDao);
            attachment.setLabType(reference.source());
        }
        super.persist(model);
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
        Query q = entityManager.createQuery("SELECT cd, plr FROM ConsultDocs cd, PatientLabRouting plr WHERE plr.labNo = cd.documentNo AND plr.labType = cd.labType AND EXISTS (select owner.id from ConsultationRequest owner where owner.id = cd.requestId and owner.demographicId = plr.demographicNo) AND cd.requestId = :consultationId AND cd.docType = :docType AND cd.deleted IS NULL ORDER BY cd.documentNo");
        q.setParameter("consultationId", consultationId);
        q.setParameter("docType", ConsultDocs.DOCTYPE_LAB);
        return q.getResultList();
    }
    @Override
    public List<ConsultDocs> findByRequestIdDocTypeForUpdate(Integer id, String docType) {
        return entityManager.createQuery("select x from ConsultDocs x where x.requestId = :id and x.docType = :docType and x.deleted is null", ConsultDocs.class)
                .setParameter("id", id).setParameter("docType", docType)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

}
