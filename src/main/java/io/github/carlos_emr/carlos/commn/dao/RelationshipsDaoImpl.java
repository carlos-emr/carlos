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

import io.github.carlos_emr.carlos.commn.model.Relationships;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.util.ConversionUtils;

@Repository
public class RelationshipsDaoImpl extends AbstractDaoImpl<Relationships> implements RelationshipsDao {

    public RelationshipsDaoImpl() {
        super(Relationships.class);
    }

    @Override
    public Relationships findActive(Integer id) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " r WHERE r.id = ?1 AND (r.deleted IS NULL OR r.deleted = '0')");
        query.setParameter(1, id);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<Relationships> findByDemographicNumber(Integer demographicNumber) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " r WHERE r.demographicNo = ?1 AND (r.deleted IS NULL OR r.deleted = '0')");
        query.setParameter(1, demographicNumber);
        return query.getResultList();
    }

    @Override
    public List<Relationships> findActiveSubDecisionMaker(Integer demographicNumber) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " r WHERE r.demographicNo = ?1 AND r.subDecisionMaker = ?2 AND (r.deleted IS NULL OR r.deleted = '0')");
        query.setParameter(1, demographicNumber);
        query.setParameter(2, ConversionUtils.toBoolString(Boolean.TRUE));
        return query.getResultList();
    }

    @Override
    public List<Relationships> findActiveByDemographicNumberAndFacility(Integer demographicNumber, Integer facilityId) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " r WHERE r.demographicNo = ?1 AND r.facilityId = ?2 AND (r.deleted IS NULL OR r.deleted = '0')");
        query.setParameter(1, demographicNumber);
        query.setParameter(2, facilityId);
        return query.getResultList();
    }
}
