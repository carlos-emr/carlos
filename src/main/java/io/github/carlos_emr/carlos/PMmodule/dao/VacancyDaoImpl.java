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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * JPA-based implementation of {@link VacancyDao} for managing {@link Vacancy} entities.
 *
 * @since 2001-09-17
 * @see VacancyDao
 */
@Repository
public class VacancyDaoImpl extends AbstractDaoImpl<Vacancy> implements VacancyDao {

    /** Default constructor initializing with Vacancy entity class. */
    public VacancyDaoImpl() {
        super(Vacancy.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vacancy> getVacanciesByWlProgramId(Integer wlProgramId) {
        String sqlCommand = "select x from Vacancy x where x.wlProgramId=?1 order by x.name";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, wlProgramId);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vacancy> getVacanciesByWlProgramIdAndStatus(Integer wlProgramId, String status) {
        String sqlCommand = "select x from Vacancy x where x.wlProgramId=?1 and x.status=?2 order by x.name";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, wlProgramId);
        query.setParameter(2, status);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vacancy> getVacanciesByName(String vacancyName) {
        String sqlCommand = "select x from Vacancy x where x.name=?1 order by x.name";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, vacancyName);

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vacancy> findByStatusAndVacancyId(String status, int vacancyId) {
        String sqlCommand = "select x from Vacancy x where x.status=?1 and x.id=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, status);
        query.setParameter(2, vacancyId);

        return query.getResultList();
    }

    @Override
    public Vacancy getVacancyById(int vacancyId) {
        String sqlCommand = "select x from Vacancy x where x.id=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, vacancyId);

        List<Vacancy> l = query.getResultList();
        if (l != null && !l.isEmpty())
            return l.get(0);
        else
            return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Vacancy> findCurrent() {
        String sqlCommand = "select x from Vacancy x where x.status=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, "ACTIVE");

        return query.getResultList();
    }
}
