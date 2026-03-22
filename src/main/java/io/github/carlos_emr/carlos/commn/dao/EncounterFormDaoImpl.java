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

import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link EncounterFormDao} for clinical encounter data access.
 *
 * @since 2001
 */

public class EncounterFormDaoImpl extends AbstractDaoImpl<EncounterForm> implements EncounterFormDao {

    /** Constructs this DAO for the {@link EncounterForm} entity class. */

    public EncounterFormDaoImpl() {
        super(EncounterForm.class);
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<EncounterForm> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<EncounterForm> results = query.getResultList();
        return results;
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<EncounterForm> findAllNotHidden() {
        Query query = entityManager
                .createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x where x.displayOrder!=0");
        List<EncounterForm> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<EncounterForm> findByFormName(String formName) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.formName=?1");
        query.setParameter(1, formName);

        @SuppressWarnings("unchecked")
        List<EncounterForm> results = query.getResultList();

        return (results);
    }

    /** {@inheritDoc} */

    @Override
    public List<EncounterForm> findByFormTable(String formTable) {
        Query query = entityManager
                .createQuery("select x from " + modelClass.getSimpleName() + " x where x.formTable=?1");
        query.setParameter(1, formTable);

        @SuppressWarnings("unchecked")
        List<EncounterForm> results = query.getResultList();

        return (results);
    }

}
