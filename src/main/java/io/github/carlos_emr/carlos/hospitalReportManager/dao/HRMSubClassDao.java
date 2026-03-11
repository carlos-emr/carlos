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

package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import java.util.List;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMSubClass;
import org.springframework.stereotype.Repository;

@Repository
public class HRMSubClassDao extends AbstractDaoImpl<HRMSubClass> {

    public HRMSubClassDao() {
        super(HRMSubClass.class);
    }

    public List<HRMSubClass> listAll() {
        String sql = "select x from " + this.modelClass.getName() + " x ";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<HRMSubClass> subclasses = query.getResultList();
        return subclasses;
    }

    public HRMSubClass findSubClassMapping(String className, String subClassName, String subClassMnemonic, String sendingFacilityId) {
        StringBuilder sql = new StringBuilder("select x from HRMSubClass x where x.className = :cls and x.subClassName = :sub and x.sendingFacilityId = :sf");

        if (subClassMnemonic != null) {
            sql.append(" and x.subClassMnemonic = :mn");
        }

        TypedQuery<HRMSubClass> q = entityManager.createQuery(sql.toString(), HRMSubClass.class)
                .setParameter("cls", className)
                .setParameter("sub", subClassName)
                .setParameter("sf", sendingFacilityId);

        if (subClassMnemonic != null) {
            q.setParameter("mn", subClassMnemonic);
        }

        q.setMaxResults(1);
        List<HRMSubClass> results = q.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private HRMSubClass findSubClassMappingIgnoreFacility(String className, String subClassName, String subClassMnemonic) {
        StringBuilder sql = new StringBuilder("select x from HRMSubClass x where x.className = :cls and x.subClassName = :sub");

        if (subClassMnemonic != null) {
            sql.append(" and x.subClassMnemonic = :mn");
        }

        TypedQuery<HRMSubClass> q = entityManager.createQuery(sql.toString(), HRMSubClass.class)
                .setParameter("cls", className)
                .setParameter("sub", subClassName);

        if (subClassMnemonic != null) {
            q.setParameter("mn", subClassMnemonic);
        }

        q.setMaxResults(1);
        List<HRMSubClass> results = q.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public HRMSubClass findApplicableSubClassMapping(String className, String subClassName, String subClassMnemonic, String sendingFacilityId) {
        // First try exact match with facilityId
        HRMSubClass mapping = findSubClassMapping(className, subClassName, subClassMnemonic, sendingFacilityId);

        // If none, fall back to wildcard (ignore facility)
        if (mapping == null) {
            mapping = findSubClassMappingIgnoreFacility(className, subClassName, subClassMnemonic);
        }

        return mapping;
    }


}
