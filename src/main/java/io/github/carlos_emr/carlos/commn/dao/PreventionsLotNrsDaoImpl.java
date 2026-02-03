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

import io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs;
import org.springframework.stereotype.Repository;

@Repository
public class PreventionsLotNrsDaoImpl extends AbstractDaoImpl<PreventionsLotNrs> implements PreventionsLotNrsDao {

    public PreventionsLotNrsDaoImpl() {
        super(PreventionsLotNrs.class);
    }

    @Override
    public List<PreventionsLotNrs> findLotNrData(Boolean bDeleted) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from PreventionsLotNrs x");
        if (bDeleted != null) sb.append(" where x.deleted=?1");
        sb.append(" order by x.preventionType, x.lotNr");
        Query query = entityManager.createQuery(sb.toString());
        query.setParameter(1, bDeleted);

        @SuppressWarnings("unchecked")
        List<PreventionsLotNrs> pList = query.getResultList();

        return (pList);
    }

    @Override
    public PreventionsLotNrs findByName(String prevention, String lotNr, Boolean bDeleted) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from PreventionsLotNrs x where x.preventionType=?1 and x.lotNr=?2");
        if (bDeleted != null) sb.append(" and x.deleted=?3");
        sb.append(" order by x.lotNr");
        Query query = entityManager.createQuery(sb.toString());
        query.setParameter(1, prevention);
        query.setParameter(2, lotNr);
        if (bDeleted != null) query.setParameter(3, bDeleted);

        return this.getSingleResultOrNull(query);
    }

    @Override
    public List<String> findLotNrs(String prevention, Boolean bDeleted) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x.lotNr from PreventionsLotNrs x where x.preventionType=?1");
        if (bDeleted != null) sb.append(" and x.deleted=?2");
        sb.append(" order by x.lotNr");
        Query query = entityManager.createQuery(sb.toString());
        query.setParameter(1, prevention);
        if (bDeleted != null) query.setParameter(2, bDeleted);

        @SuppressWarnings("unchecked")
        List<String> pList = query.getResultList();

        return (pList);
    }

    @Override
    public List<PreventionsLotNrs> findPagedData(String prevention, Boolean bDeleted, Integer offset, Integer limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("select x from PreventionsLotNrs x where lower(x.preventionType) like ?1");
        if (bDeleted != null) sb.append(" and x.deleted=?2");
        sb.append(" order by x.preventionType, x.lotNr");
        Query query = entityManager.createQuery(sb.toString());
        query.setParameter(1, prevention);
        if (bDeleted != null) query.setParameter(2, bDeleted);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<PreventionsLotNrs> pList = query.getResultList();

        return (pList);
    }
}
