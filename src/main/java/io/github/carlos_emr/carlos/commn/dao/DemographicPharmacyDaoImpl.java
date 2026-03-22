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

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link DemographicPharmacyDao} for patient demographic data access.
 *
 * @since 2001
 */

public class DemographicPharmacyDaoImpl extends AbstractDaoImpl<DemographicPharmacy> implements DemographicPharmacyDao {

    public DemographicPharmacyDaoImpl() {
        super(DemographicPharmacy.class);
    }

    /** {@inheritDoc} */

    @Override
    public DemographicPharmacy addPharmacyToDemographic(Integer pharmacyId, Integer demographicNo,
                                                        Integer preferredOrder) {

        String sql = "select x from DemographicPharmacy x where x.status = ?1 and x.demographicNo = ?2 and x.pharmacyId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, DemographicPharmacy.ACTIVE);
        query.setParameter(2, demographicNo);
        query.setParameter(3, pharmacyId);
        DemographicPharmacy demographicPharmacy = getSingleResultOrNull(query);
        int currentOrder;
        if (demographicPharmacy != null) {

            int min, max;
            currentOrder = demographicPharmacy.getPreferredOrder();
            min = currentOrder > preferredOrder ? preferredOrder : currentOrder;
            max = currentOrder > preferredOrder ? currentOrder : preferredOrder;

            sql = "select x from DemographicPharmacy x where x.status = ?1 and x.demographicNo = ?2 and x.preferredOrder >= ?3 and x.preferredOrder < ?4";
            query = entityManager.createQuery(sql);
            query.setParameter(1, DemographicPharmacy.ACTIVE);
            query.setParameter(2, demographicNo);
            query.setParameter(3, min);
            query.setParameter(4, max);
        } else {
            sql = "select x from DemographicPharmacy x where x.status = ?1 and x.demographicNo = ?2 and x.preferredOrder >= ?3";
            query = entityManager.createQuery(sql);
            query.setParameter(1, DemographicPharmacy.ACTIVE);
            query.setParameter(2, demographicNo);
            query.setParameter(3, preferredOrder);
        }

        @SuppressWarnings("unchecked")
        List<DemographicPharmacy> results = query.getResultList();

        for (DemographicPharmacy demographicPharmacy2 : results) {
            currentOrder = demographicPharmacy2.getPreferredOrder();
            ++currentOrder;
            if (currentOrder > 10) {
                demographicPharmacy2.setStatus(DemographicPharmacy.INACTIVE);
            }

            demographicPharmacy2.setPreferredOrder(currentOrder);
            merge(demographicPharmacy2);
        }

        if (demographicPharmacy == null) {

            DemographicPharmacy dp = new DemographicPharmacy();
            dp.setAddDate(new Date());
            dp.setStatus(DemographicPharmacy.ACTIVE);
            dp.setDemographicNo(demographicNo);
            dp.setPharmacyId(pharmacyId);
            dp.setPreferredOrder(preferredOrder);
            persist(dp);
            return dp;
        } else {

            demographicPharmacy.setPreferredOrder(preferredOrder);
            merge(demographicPharmacy);
            return demographicPharmacy;
        }

    }

    /** {@inheritDoc} */

    @Override
    public void unlinkPharmacy(Integer pharmacyId, Integer demographicNo) {

        String sql = "select x from DemographicPharmacy x where x.status = ?1 and x.demographicNo = ?2 and x.pharmacyId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, DemographicPharmacy.ACTIVE);
        query.setParameter(2, demographicNo);
        query.setParameter(3, pharmacyId);
        DemographicPharmacy demographicPharmacy = getSingleResultOrNull(query);

        if (demographicPharmacy != null) {

            demographicPharmacy.setStatus("0");
            merge(demographicPharmacy);

            sql = "select x from DemographicPharmacy x where x.status = ?1 and x.demographicNo = ?2 and x.preferredOrder > ?3";
            query = entityManager.createQuery(sql);
            query.setParameter(1, DemographicPharmacy.ACTIVE);
            query.setParameter(2, demographicNo);
            query.setParameter(3, demographicPharmacy.getPreferredOrder());

            @SuppressWarnings("unchecked")
            List<DemographicPharmacy> demographicPharmacies = query.getResultList();

            int preferredOrder;
            for (DemographicPharmacy demographicPharmacy2 : demographicPharmacies) {
                preferredOrder = demographicPharmacy2.getPreferredOrder();
                --preferredOrder;
                demographicPharmacy2.setPreferredOrder(preferredOrder);
                merge(demographicPharmacy2);
            }

        } else {
            MiscUtils.getLogger().error("UNKNOWN PHARMACY TO UNLINK");
        }
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicPharmacy> findByDemographicId(Integer demographicNo) {
        String sql = "select x from DemographicPharmacy x where x.status=?1 and x.demographicNo=?2 order by x.preferredOrder";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, DemographicPharmacy.ACTIVE);
        query.setParameter(2, demographicNo);
        @SuppressWarnings("unchecked")
        List<DemographicPharmacy> results = query.getResultList();
        return results;
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<DemographicPharmacy> findAllByDemographicId(Integer demographicNo) {
        Query query = createQuery("dp", "dp.demographicNo = ?1 AND dp.status = '1'");
        query.setParameter(1, demographicNo);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<DemographicPharmacy> findAllByPharmacyId(Integer pharmacyId) {

        String sql = "select x from DemographicPharmacy x where x.pharmacyId = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, pharmacyId);

        return query.getResultList();

    }

    /** {@inheritDoc} */

    @Override
    public Long getTotalDemographicsPreferedToPharmacyByPharmacyId(Integer pharmacyId) {
        String sql = "SELECT COUNT(*) FROM DemographicPharmacy x WHERE x.pharmacyId = ?1 AND x.status = '1'";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, pharmacyId);

        return (Long) query.getSingleResult();
    }
}
