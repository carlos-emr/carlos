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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class EFormDocsDaoImpl extends AbstractDaoImpl<EFormDocs> implements EFormDocsDao {

    public EFormDocsDaoImpl() {
        super(EFormDocs.class);
    }

    public List<EFormDocs> findByFdidIdDocNoDocType(Integer fdid, Integer documentNo, String docType) {
        String sql = "select x from EFormDocs x where x.fdid=?1 and x.documentNo=?2 and x.docType=?3 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, fdid);
        query.setParameter(2, documentNo);
        query.setParameter(3, docType);

        List<EFormDocs> results = query.getResultList();
        return results;
    }

    public List<EFormDocs> findByFdidIdDocType(Integer fdid, String docType) {
        String sql = "select x from EFormDocs x where x.fdid=?1 and x.docType=?2 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, fdid);
        query.setParameter(2, docType);

        return query.getResultList();
    }

    public List<EFormDocs> findByFdid(Integer fdid) {
        String sql = "select x from EFormDocs x where x.fdid=?1 and x.deleted is NULL";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, fdid);

        List<EFormDocs> results = query.getResultList();
        return results;
    }

    public List<Object[]> findLabs(Integer fdid) {
        String sql = "FROM EFormDocs cd, PatientLabRouting plr " +
                "WHERE plr.labNo = cd.documentNo " +
                "AND cd.fdid = ?1" +
                "AND cd.docType = ?2" +
                "AND cd.deleted IS NULL " +
                "ORDER BY cd.documentNo";
        Query q = entityManager.createQuery(sql);
        q.setParameter(1, fdid);
        q.setParameter(2, EFormDocs.DOCTYPE_LAB);
        return q.getResultList();
    }
}
