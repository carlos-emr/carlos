//CHECKSTYLE:OFF
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

import java.util.Collection;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import org.springframework.stereotype.Repository;

@Repository
public class DemographicSetsDaoImpl extends AbstractDaoImpl<DemographicSets> implements DemographicSetsDao {

    public DemographicSetsDaoImpl() {
        super(DemographicSets.class);
    }

    @Override
    public List<DemographicSets> findBySetName(String setName) {
        String sql = "select x from DemographicSets x where x.archive != ?1 and x.name=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "1");
        query.setParameter(2, setName);
        @SuppressWarnings("unchecked")
        List<DemographicSets> results = query.getResultList();
        return results;
    }

    @Override
    public List<DemographicSets> findBySetNames(Collection<String> setNameList) {
        String sql = "select x from DemographicSets x where x.archive != ?1 and x.name in (?2)";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "1");
        query.setParameter(2, setNameList);
        @SuppressWarnings("unchecked")
        List<DemographicSets> results = query.getResultList();
        return results;
    }

    @Override
    public List<DemographicSets> findBySetNameAndEligibility(String setName, String eligibility) {
        String sql = "select x from DemographicSets x where x.name = ?1 and x.eligibility=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, setName);
        query.setParameter(2, eligibility);
        @SuppressWarnings("unchecked")
        List<DemographicSets> results = query.getResultList();
        return results;
    }

    @Override
    public List<String> findSetNamesByDemographicNo(Integer demographicNo) {
        String sql = "select distinct(x.name) from DemographicSets x where x.archive = ?1 and x.demographicNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "1");
        query.setParameter(2, demographicNo);
        @SuppressWarnings("unchecked")
        List<String> results = query.getResultList();
        return results;
    }

    @Override
    public List<String> findSetNames() {
        String sql = "select distinct(x.name) from DemographicSets x";
        Query query = entityManager.createQuery(sql);
        @SuppressWarnings("unchecked")
        List<String> results = query.getResultList();
        return results;
    }

    @Override
    public List<DemographicSets> findBySetNameAndDemographicNo(String setName, int demographicNo) {
        String sql = "select x from DemographicSets x where x.name = ?1 and x.demographicNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, setName);
        query.setParameter(2, demographicNo);
        @SuppressWarnings("unchecked")
        List<DemographicSets> results = query.getResultList();
        return results;
    }
}
