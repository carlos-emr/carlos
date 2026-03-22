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

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMSubClass;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMSubClass} entities, providing lookups for
 * HRM report sub-class mappings by class name, mnemonic, and sending facility.
 *
 * @see HRMSubClass
 * @since 2008-11-05
 */
@Repository
public class HRMSubClassDao extends AbstractDaoImpl<HRMSubClass> {

    public HRMSubClassDao() {
        super(HRMSubClass.class);
    }

    /**
     * Returns all HRM sub-class mappings.
     *
     * @return List&lt;HRMSubClass&gt; all sub-class mapping records
     */
    public List<HRMSubClass> listAll() {
        String sql = "select x from " + this.modelClass.getName() + " x ";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<HRMSubClass> subclasses = query.getResultList();
        return subclasses;
    }

    /**
     * Finds an exact sub-class mapping by class name, sub-class name, mnemonic,
     * and sending facility ID.
     *
     * @param className String the report class name (e.g. "Diagnostic Imaging Report")
     * @param subClassName String the sub-class name
     * @param subClassMnemonic String the sub-class mnemonic, or {@code null} to omit from the match
     * @param sendingFacilityId String the sending facility identifier
     * @return HRMSubClass the matching mapping, or {@code null} if not found
     */
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

    /**
     * Finds the best-matching sub-class mapping, trying an exact facility match first,
     * then falling back to a facility-agnostic match.
     *
     * @param className String the report class name
     * @param subClassName String the sub-class name
     * @param subClassMnemonic String the sub-class mnemonic, or {@code null}
     * @param sendingFacilityId String the sending facility identifier
     * @return HRMSubClass the best matching mapping, or {@code null} if none found
     */
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
