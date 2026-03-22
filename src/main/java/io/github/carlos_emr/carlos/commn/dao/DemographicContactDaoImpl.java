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

import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link DemographicContactDao} for patient demographic data access.
 *
 * @since 2001
 */

public class DemographicContactDaoImpl extends AbstractDaoImpl<DemographicContact> implements DemographicContactDao {

    /** Constructs this DAO for the {@link DemographicContact} entity class. */

    public DemographicContactDaoImpl() {
        super(DemographicContact.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findByDemographicNo(int demographicNo) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.demographicNo=?1 and x.deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findActiveByDemographicNo(int demographicNo) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.demographicNo=?1 and x.deleted=false and x.active=true";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findByDemographicNoAndCategory(int demographicNo, String category) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.demographicNo=?1 and x.category=?2 and x.deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, category);
        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> find(int demographicNo, int contactId) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.demographicNo=?1 and x.contactId = ?2 and x.deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, Integer.valueOf(contactId).toString());
        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findAllByContactIdAndCategoryAndType(int contactId, String category, int type) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.contactId = ?1 and x.category = ?2 and x.type = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, Integer.valueOf(contactId).toString());
        query.setParameter(2, category);
        query.setParameter(3, type);

        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findAllByDemographicNoAndCategoryAndType(int demographicNo, String category,
                                                                             int type) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.demographicNo = ?1 and x.category = ?2 and x.type = ?3 and x.active=true and deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, category);
        query.setParameter(3, type);

        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        if (dContacts == null) {
            dContacts = Collections.emptyList();
        }
        return dContacts;
    }

    /** {@inheritDoc} */

    @Override
    public List<DemographicContact> findSDMByDemographicNo(int demographicNo) {
        String sql = "select x from " + this.modelClass.getName()
                + " x where x.demographicNo = ?1 and x.sdm = 'true'  and x.active=true and deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<DemographicContact> dContacts = query.getResultList();
        if (dContacts == null) {
            dContacts = Collections.emptyList();
        }
        return dContacts;
    }
}
