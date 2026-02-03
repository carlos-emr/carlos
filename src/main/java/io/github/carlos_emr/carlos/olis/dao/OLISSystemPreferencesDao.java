/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.olis.dao;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.olis.model.OLISSystemPreferences;
import org.springframework.stereotype.Repository;

@Repository
public class OLISSystemPreferencesDao extends AbstractDaoImpl<OLISSystemPreferences> {


    public OLISSystemPreferencesDao() {
        super(OLISSystemPreferences.class);
    }

    public OLISSystemPreferences getPreferences() {
        try {
            String sql = "select x from " + this.modelClass.getName() + " x";
            Query query = entityManager.createQuery(sql);
            return (OLISSystemPreferences) query.getSingleResult();
        } catch (javax.persistence.NoResultException nre) {
            return new OLISSystemPreferences();
        }
    }

    public void save(OLISSystemPreferences olisPrefs) {
        entityManager.merge(olisPrefs);
    }
}
