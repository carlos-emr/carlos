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

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
/**
 * JPA implementation of {@link ConsultResponseDocDao} for consultation and referral data access.
 *
 * @since 2001
 */

public class ConsultResponseDocDaoImpl extends AbstractDaoImpl<ConsultResponseDoc> implements ConsultResponseDocDao {
    public ConsultResponseDocDaoImpl() {
        super(ConsultResponseDoc.class);
    }

    public ConsultResponseDoc findByResponseIdDocNoDocType(Integer responseId, Integer documentNo, String docType) {
        String sql = "select x from ConsultResponseDoc x where x.responseId=?1 and x.documentNo=?2 and x.docType=?3 and x.deleted IS NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, responseId);
        query.setParameter(2, documentNo);
        query.setParameter(3, docType);

        List<ConsultResponseDoc> results = query.getResultList();
        if (results != null && results.size() > 0) return results.get(0);
        else return null;
    }

    public List<ConsultResponseDoc> findByResponseId(Integer responseId) {
        String sql = "select x from ConsultResponseDoc x where x.responseId=?1 and x.deleted IS NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, responseId);

        List<ConsultResponseDoc> results = query.getResultList();
        return results;
    }

    public List<Object[]> findLabs(Integer consultResponseId) {
        Query q = entityManager.createQuery("SELECT crd, plr FROM ConsultResponseDoc crd, PatientLabRouting plr WHERE plr.labNo = crd.documentNo AND crd.responseId = ?1 AND crd.docType = ?2 AND crd.deleted IS NULL ORDER BY crd.documentNo");
        q.setParameter(1, consultResponseId);
        q.setParameter(2, ConsultResponseDoc.DOCTYPE_LAB);
        return q.getResultList();
    }
}
