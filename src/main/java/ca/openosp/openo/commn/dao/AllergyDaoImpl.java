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

package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.Allergy;

import javax.persistence.Query;
import java.util.Date;
import java.util.List;

public class AllergyDaoImpl extends AbstractDaoImpl<Allergy> implements AllergyDao {

    public AllergyDaoImpl() {
        super(Allergy.class);
    }

    @Override
    public List<Allergy> findAllergies(Integer demographic_no) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicNo=?1 order by x.archived,x.severityOfReaction desc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographic_no);

        @SuppressWarnings("unchecked")
        List<Allergy> allergies = query.getResultList();
        return allergies;
    }

    @Override
    public List<Allergy> findActiveAllergies(Integer demographic_no) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicNo=?1 and x.archived = 0 order by x.severityOfReaction";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographic_no);

        @SuppressWarnings("unchecked")
        List<Allergy> allergies = query.getResultList();
        return allergies;
    }

    @Override
    public List<Allergy> findActiveAllergiesOrderByDescription(Integer demographic_no) {
        String sql = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicNo=?1 and x.archived = 0 order by x.description";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographic_no);

        @SuppressWarnings("unchecked")
        List<Allergy> allergies = query.getResultList();
        return allergies;
    }

    @Override
    public List<Allergy> findByDemographicIdUpdatedAfterDate(Integer demographicId, Date updatedAfterThisDate) {
        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicNo=?1 and x.lastUpdateDate>?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDate);

        @SuppressWarnings("unchecked")
        List<Allergy> results = query.getResultList();

        return (results);
    }

    // for integrator
    @Override
    public List<Integer> findDemographicIdsUpdatedAfterDate(Date updatedAfterThisDate) {
        String sqlCommand = "select x.demographicNo from Allergy x where x.lastUpdateDate>?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, updatedAfterThisDate);

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();

        return (results);
    }

    /**
     * @return results ordered by lastUpdateDate
     */
    @Override
    public List<Allergy> findByUpdateDate(Date updatedAfterThisDateInclusive, int itemsToReturn) {
        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.lastUpdateDate>=?1 order by x.lastUpdateDate";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, updatedAfterThisDateInclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Allergy> results = query.getResultList();
        return (results);
    }

    /**
     * @return results ordered by lastUpdateDate asc
     */
    @Override
    public List<Allergy> findByProviderDemographicLastUpdateDate(String providerNo, Integer demographicId,
                                                                 Date updatedAfterThisDateExclusive, int itemsToReturn) {
        // the providerNo field is always blank right now... we have no idea which
        // providers did the allery entry
        // String sqlCommand = "select x from "+modelClass.getSimpleName()+" x where
        // x.demographicNo=?1 and x.providerNo=?2 and x.lastUpdateDate>?3 order by
        // x.lastUpdateDate";

        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.demographicNo=?1 and x.lastUpdateDate>?2 order by x.lastUpdateDate";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicId);
        query.setParameter(2, updatedAfterThisDateExclusive);
        setLimit(query, itemsToReturn);

        @SuppressWarnings("unchecked")
        List<Allergy> results = query.getResultList();
        return (results);
    }

    @Override
    public List<Allergy> findAllCustomAllergiesWithNullNonDrugFlag(int start, int limit) {
        String sqlCommand = "select x from " + modelClass.getSimpleName()
                + " x where x.typeCode=0 and x.nonDrug is NULL order by x.demographicNo";

        Query query = entityManager.createQuery(sqlCommand);
        query.setFirstResult(start);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<Allergy> results = query.getResultList();
        return (results);
    }
}
