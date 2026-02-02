//CHECKSTYLE:OFF
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
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import org.springframework.stereotype.Repository;

@Repository
public class ClinicNbrDaoImpl extends AbstractDaoImpl<ClinicNbr> implements ClinicNbrDao {

    public ClinicNbrDaoImpl() {
        super(ClinicNbr.class);
    }

    @Override
    public ArrayList<ClinicNbr> findAll() {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where nbr_status != 'D' order by nbr_value asc");
        
        @SuppressWarnings("unchecked")
        ArrayList<ClinicNbr> results = new ArrayList<ClinicNbr>(query.getResultList());
        return (results);
    }

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
