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

import io.github.carlos_emr.carlos.commn.model.AppointmentSearch;
import org.springframework.stereotype.Repository;

/**
 * JPA implementation of {@link AppointmentSearchDao} for managing appointment search configurations.
 *
 * @since 2001
 */
@Repository
public class AppointmentSearchDaoImpl extends AbstractDaoImpl<AppointmentSearch> implements AppointmentSearchDao {

    /** Constructs the appointment search DAO for the {@link AppointmentSearch} entity class. */
    public AppointmentSearchDaoImpl() {
        super(AppointmentSearch.class);
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */
    @Override
    public List<AppointmentSearch> findAll() {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " a order by a.createDate desc");
        return query.getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<AppointmentSearch> findActive() {
        Query q = entityManager.createQuery("select a from AppointmentSearch a where a.active= true");
        @SuppressWarnings("unchecked")
        List<AppointmentSearch> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */
    @Override
    public List<AppointmentSearch> findByUUID(String uuid, Boolean active) {
        String queryStr = "select a from AppointmentSearch a where a.uuid=?1";
        if (active != null && active.booleanValue()) {
            queryStr = queryStr + " and a.active = true";
        }
        Query q = entityManager.createQuery(queryStr);
        q.setParameter(1, uuid);

        @SuppressWarnings("unchecked")
        List<AppointmentSearch> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */
    @Override
    public AppointmentSearch findForProvider(String providerNo) {
        if (providerNo == null || providerNo.length() == 0) {
            return null;
        }

        Query q = entityManager.createQuery(
                "select a from AppointmentSearch a where a.providerNo = ?1 and a.active = true order by a.updateDate desc");
        q.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<AppointmentSearch> results = q.getResultList();

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

}
