/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package io.github.carlos_emr.carlos.olis.dao;

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.olis.model.OLISProviderPreferences;
import org.springframework.stereotype.Repository;

@Repository
public class OLISProviderPreferencesDao extends AbstractDaoImpl<OLISProviderPreferences> {


    public OLISProviderPreferencesDao() {
        super(OLISProviderPreferences.class);
    }

    public OLISProviderPreferences findById(String id) {
        try {
            String sql = "select x from " + this.modelClass.getName() + " x where x.providerId=?1";
            Query query = entityManager.createQuery(sql);
            query.setParameter(1, id);
            return (OLISProviderPreferences) query.getSingleResult();
        } catch (javax.persistence.NoResultException nre) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<OLISProviderPreferences> findAll() {
        String sql = "select x from " + this.modelClass.getName() + " x";
        Query query = entityManager.createQuery(sql);
        return query.getResultList();
    }

}
