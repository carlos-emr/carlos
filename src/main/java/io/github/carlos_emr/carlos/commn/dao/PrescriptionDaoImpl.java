//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Prescription;
import org.springframework.stereotype.Repository;

@Repository
public class PrescriptionDaoImpl extends AbstractDaoImpl<Prescription> implements PrescriptionDao {

    public PrescriptionDaoImpl() {
        super(Prescription.class);
    }

    @Override
    public List<Prescription> findByDemographicId(Integer demographicId) {

        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);

        @SuppressWarnings("unchecked")
        List<Prescription> results = query.getResultList();
        return (results);
    }

    @Override
    public List<Prescription> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date afterThisDate) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1 and x.lastUpdateDate>=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, afterThisDate);

        @SuppressWarnings("unchecked")
        List<Prescription> results = query.getResultList();
        return (results);
    }

    @Override
    public List<Prescription> findByDemographicIdUpdatedAfterDateExclusive(Integer demographicId, Date afterThisDate) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1 and x.lastUpdateDate>?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, afterThisDate);

        @SuppressWarnings("unchecked")
        List<Prescription> results = query.getResultList();
        return (results);
    }

    @Override
    public int updatePrescriptionsByScriptNo(Integer scriptNo, String comment) {
        Query query = entityManager.createQuery("UPDATE Prescription p SET p.comments = ?1 WHERE p.id = ?2");
        query.setParameter(1, comment);
        query.setParameter(2, scriptNo);
        return query.executeUpdate();
    }

    /**
     * @return results ordered by lastUpdateDate
     */
    @Override
    public List<Prescription> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.lastUpdateDate>?1 order by x.lastUpdateDate";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Prescription> results = query.getResultList();
        return (results);
    }

    /**
     * @return results ordered by lastUpdateDate asc
     */
    @Override
    public List<Prescription> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                      Date updatedAfterThisDateExclusive, int itemsToReturn) {
        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1 and x.providerNo=?2 and x.lastUpdateDate>?3 order by x.lastUpdateDate";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, providerNo);
        query.setParameter(3, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Prescription> results = query.getResultList();
        return (results);
    }

}
