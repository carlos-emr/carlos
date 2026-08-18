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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EncounterTemplateDaoImpl extends AbstractDaoImpl<EncounterTemplate> implements EncounterTemplateDao {

    public EncounterTemplateDaoImpl() {
        super(EncounterTemplate.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<EncounterTemplate> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<EncounterTemplate> results = query.getResultList();
        return results;
    }

    @Override
    public List<EncounterTemplate> findByName(String name) {
        Query query = entityManager.createQuery("select x from EncounterTemplate x where x.encounterTemplateName like ?1");
        query.setParameter(1, name);

        @SuppressWarnings("unchecked")
        List<EncounterTemplate> results = query.getResultList();

        return results;
    }

    @Override
    public List<EncounterTemplate> findByName(String name, Integer startIndex, Integer itemsToReturn) {
        Query query = entityManager.createQuery("select x from EncounterTemplate x where x.encounterTemplateName like ?1");
        query.setParameter(1, name);

        query.setFirstResult(startIndex);
        query.setMaxResults(itemsToReturn);

        @SuppressWarnings("unchecked")
        List<EncounterTemplate> results = query.getResultList();

        return results;
    }
}
