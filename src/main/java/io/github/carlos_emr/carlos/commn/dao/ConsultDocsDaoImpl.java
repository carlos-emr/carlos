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
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class ConsultDocsDaoImpl extends AbstractDaoImpl<ConsultDocs> implements ConsultDocsDao {

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
        String sql = "FROM ConsultDocs cd, PatientLabRouting plr " +
                "WHERE plr.labNo = cd.documentNo " +
                "AND cd.requestId = :consultationId " +
                "AND cd.docType = :docType " +
                "AND cd.deleted IS NULL " +
                "ORDER BY cd.documentNo";
        Query q = entityManager.createQuery(sql);
        q.setParameter("consultationId", consultationId);
        q.setParameter("docType", ConsultDocs.DOCTYPE_LAB);
        return q.getResultList();
    }
}
