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

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMDocumentSubClass} entities, managing sub-class
 * records associated with individual HRM documents (e.g. Diagnostic Imaging sub-types).
 *
 * @see HRMDocumentSubClass
 * @since 2008-11-05
 */
@Repository
public class HRMDocumentSubClassDao extends AbstractDaoImpl<HRMDocumentSubClass> {

    public HRMDocumentSubClassDao() {
        super(HRMDocumentSubClass.class);
    }

    /**
     * Returns all sub-classes associated with a given HRM document.
     *
     * @param id Integer the HRM document ID
     * @return List&lt;HRMDocumentSubClass&gt; the sub-class records for the document
     */
    public List<HRMDocumentSubClass> getSubClassesByDocumentId(Integer id) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<HRMDocumentSubClass> subClasses = query.getResultList();
        return subClasses;
    }

    /**
     * Returns only the active sub-classes associated with a given HRM document.
     *
     * @param id Integer the HRM document ID
     * @return List&lt;HRMDocumentSubClass&gt; the active sub-class records
     */
    public List<HRMDocumentSubClass> getActiveSubClassesByDocumentId(Integer id) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.isActive=1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<HRMDocumentSubClass> subClasses = query.getResultList();
        return subClasses;
    }

    /**
     * Deactivates all sub-classes for a given HRM document, typically before
     * setting a new active sub-class.
     *
     * @param id Integer the HRM document ID
     * @return boolean {@code true} if at least one record was updated
     */
    public boolean setAllSubClassesForDocumentAsInactive(Integer id) {
        String sql = "update " + this.modelClass.getName() + " x set isActive=false where x.hrmDocumentId=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.executeUpdate() > 0;
    }
}
