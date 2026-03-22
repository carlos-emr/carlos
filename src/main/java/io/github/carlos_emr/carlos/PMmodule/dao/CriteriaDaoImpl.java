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

import io.github.carlos_emr.carlos.PMmodule.model.Criteria;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * JPA-based implementation of {@link CriteriaDao} for managing {@link Criteria} entities.
 *
 * @since 2001-09-17
 * @see CriteriaDao
 * @see Criteria
 */
@Repository
public class CriteriaDaoImpl extends AbstractDaoImpl<Criteria> implements CriteriaDao {

    /** Default constructor initializing with Criteria entity class. */
    public CriteriaDaoImpl() {
        super(Criteria.class);
    }

    /** {@inheritDoc} */
    public List<Criteria> getCriteriaByTemplateId(Integer templateId) {
        Query q = entityManager.createQuery("select c from Criteria c where c.templateId=?1");
        q.setParameter(1, templateId);

        @SuppressWarnings("unchecked")
        List<Criteria> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */
    public Criteria getCriteriaByTemplateIdVacancyIdTypeId(Integer templateId, Integer vacancyId, Integer typeId) {
        if (templateId != null && vacancyId != null) {
            Query q = entityManager.createQuery("select c from Criteria c where c.templateId=?1 and c.criteriaTypeId=?2 and c.vacancyId=?3");
            q.setParameter(1, templateId);
            q.setParameter(2, typeId);
            q.setParameter(3, vacancyId);
            return this.getSingleResultOrNull(q);
        } else if (templateId == null && vacancyId != null) {
            Query q = entityManager.createQuery("select c from Criteria c where c.templateId IS NULL and c.criteriaTypeId=?1 and c.vacancyId=?2");
            q.setParameter(1, typeId);
            q.setParameter(2, vacancyId);
            return this.getSingleResultOrNull(q);
        } else if (templateId != null && vacancyId == null) {
            Query q = entityManager.createQuery("select c from Criteria c where c.templateId=?1 and c.criteriaTypeId=?2 and c.vacancyId is null");
            q.setParameter(1, templateId);
            q.setParameter(2, typeId);
            return this.getSingleResultOrNull(q);
        } else {
            return null;
        }


    }

    /** {@inheritDoc} */
    public List<Criteria> getCriteriasByVacancyId(Integer vacancyId) {
        Query q = entityManager.createQuery("select c from Criteria c where c.vacancyId=?1");
        q.setParameter(1, vacancyId);

        @SuppressWarnings("unchecked")
        List<Criteria> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */
    public List<Criteria> getRefinedCriteriasByVacancyId(Integer vacancyId) {
        Query q = entityManager.createQuery("select c from Criteria c where c.canBeAdhoc!=?1 and c.vacancyId=?2");
        q.setParameter(1, 0); //canBeAdhoc=0 means don't appear in vacancy.
        q.setParameter(2, vacancyId);

        @SuppressWarnings("unchecked")
        List<Criteria> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */
    public List<Criteria> getRefinedCriteriasByTemplateId(Integer templateId) {
        Query q = entityManager.createQuery("select c from Criteria c where c.canBeAdhoc!=?1 and c.templateId=?2");
        q.setParameter(1, 0); //canBeAdhoc=0 means don't appear in vacancy.
        q.setParameter(2, templateId);

        @SuppressWarnings("unchecked")
        List<Criteria> results = q.getResultList();

        return results;
    }


}
