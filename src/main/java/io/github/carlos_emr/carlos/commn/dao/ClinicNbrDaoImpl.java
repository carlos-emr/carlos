/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link ClinicNbrDao} for clinic data access.
 *
 * @since 2001
 */

public class ClinicNbrDaoImpl extends AbstractDaoImpl<ClinicNbr> implements ClinicNbrDao {

    public ClinicNbrDaoImpl() {
        super(ClinicNbr.class);
    }

    /** {@inheritDoc} */

    @Override
    public ArrayList<ClinicNbr> findAll() {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.nbrStatus != 'D' order by x.nbrValue asc");
        
        @SuppressWarnings("unchecked")
        ArrayList<ClinicNbr> results = new ArrayList<ClinicNbr>(query.getResultList());
        return (results);
    }

    /** {@inheritDoc} */

    @Override
    public Integer removeEntry(Integer id) {
        try {
            ClinicNbr clinicNbr = find(id);
            clinicNbr.setNbrStatus("D");
            merge(clinicNbr);
            return id;
        } catch (Exception e) {
            return 0;
        }
    }

    /** {@inheritDoc} */

    @Override
    public int addEntry(String nbrValue, String nbrString) {
        try {
            ClinicNbr clinicNbr = new ClinicNbr();
            clinicNbr.setNbrValue(nbrValue);
            clinicNbr.setNbrString(nbrString);
            persist(clinicNbr);
            return clinicNbr.getId();
        } catch (Exception e) {
            return 0;
        }
    }
}
