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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMDocumentToProvider} entities, managing the
 * association between HRM documents and healthcare providers with sign-off tracking,
 * inbox filtering, and date-based searching.
 *
 * @see HRMDocumentToProvider
 * @since 2008-11-05
 */
@Repository
public class HRMDocumentToProviderDao extends AbstractDaoImpl<HRMDocumentToProvider> {

    public HRMDocumentToProviderDao() {
        super(HRMDocumentToProvider.class);
    }

    /**
     * Finds provider-document associations for a given provider number with pagination.
     *
     * @param providerNo String the provider number
     * @param page Integer the zero-based page number
     * @param pageSize Integer the number of results per page
     * @return List&lt;HRMDocumentToProvider&gt; the matching associations
     */
    public List<HRMDocumentToProvider> findByProviderNo(String providerNo, Integer page, Integer pageSize) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.providerNo=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        query.setMaxResults(pageSize);
        query.setFirstResult(page * pageSize);
        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }


    /**
     * Finds provider-document associations with comprehensive filtering for the HRM inbox.
     *
     * <p>Supports filtering by demographic numbers, date range (using system preference
     * for date type), viewed status, signed-off status, and pagination.</p>
     *
     * @param providerNo String the provider number (supports LIKE matching)
     * @param demographicNumbers List&lt;Integer&gt; demographic numbers to filter by; use [0] for unmatched
     * @param patientSearch boolean {@code true} if filtering by patient demographics
     * @param newestDate Date the upper bound for the date filter, or {@code null}
     * @param oldestDate Date the lower bound for the date filter, or {@code null}
     * @param viewed Integer 0 for unviewed, 1 for viewed, 2 for either
     * @param signedOff Integer 0 for unsigned, 1 for signed, 2 for either
     * @param isPaged boolean {@code true} to apply pagination
     * @param page Integer the zero-based page number
     * @param pageSize Integer the number of results per page
     * @return List&lt;HRMDocumentToProvider&gt; the matching associations
     */
    public List<HRMDocumentToProvider> findByProviderNoLimit(String providerNo, List<Integer> demographicNumbers, boolean patientSearch, Date newestDate, Date oldestDate,
                                                             Integer viewed, Integer signedOff, boolean isPaged, Integer page, Integer pageSize) {

        if (patientSearch && (demographicNumbers == null || demographicNumbers.isEmpty())) {
            return Collections.emptyList();
        }

        // Building the query dynamically with JOINs and conditional parameters
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT x FROM ").append(this.modelClass.getName()).append(" x JOIN HRMDocument h ON x.hrmDocumentId = h.id ");

        boolean hasDemographics = (demographicNumbers != null && !demographicNumbers.isEmpty());
        if (hasDemographics && !(demographicNumbers.size() == 1 && demographicNumbers.get(0) == 0)) {
            sql.append("JOIN HRMDocumentToDemographic d ON x.hrmDocumentId = d.hrmDocumentId ");
        }

        sql.append("WHERE x.providerNo LIKE :providerNo ");

        // Demographic number condition
        if (hasDemographics) {
            if (demographicNumbers.size() == 1 && demographicNumbers.get(0) == 0) {
                sql.append("AND h.id NOT IN (SELECT d.hrmDocumentId FROM HRMDocumentToDemographic d) ");
            } else {
                sql.append("AND d.demographicNo IN (:demographicNumbers) ");
            }
        }

        // Retrieve date search type from system preferences
        SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
        String dateSearchType = "serviceObservation";
        SystemPreferences systemPreferences = systemPreferencesDao.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        if (systemPreferences != null && systemPreferences.getValue() != null && !systemPreferences.getValue().isEmpty()) {
            dateSearchType = systemPreferences.getValue();
        }

        // Adding date filters
        if (newestDate != null) {
            sql.append(dateSearchType.equals("receivedCreated") ? "AND h.timeReceived <= :newest " : "AND h.reportDate <= :newest ");
        }
        if (oldestDate != null) {
            sql.append(dateSearchType.equals("receivedCreated") ? "AND h.timeReceived >= :oldest " : "AND h.reportDate >= :oldest ");
        }

        // Other filters
        if (viewed != 2) {
            sql.append("AND x.viewed = :viewed ");
        }
        if (signedOff != 2) {
            sql.append("AND x.signedOff = :signedOff ");
        }

        // Construct the query and set parameters
        Query query = entityManager.createQuery(sql.toString());
        query.setParameter("providerNo", providerNo);

        if (hasDemographics && !(demographicNumbers.size() == 1 && demographicNumbers.get(0) == 0)) {
            query.setParameter("demographicNumbers", demographicNumbers);
        }
        if (newestDate != null) {
            query.setParameter("newest", newestDate);
        }
        if (oldestDate != null) {
            query.setParameter("oldest", oldestDate);
        }
        if (viewed != 2) {
            query.setParameter("viewed", viewed);
        }
        if (signedOff != 2) {
            query.setParameter("signedOff", signedOff);
        }

        // Pagination handling
        if (isPaged) {
            query.setFirstResult(page * pageSize);
            query.setMaxResults(pageSize);
        }

        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }


    /**
     * Finds all provider associations for a given HRM document.
     *
     * @param hrmDocumentId Integer the HRM document ID
     * @return List&lt;HRMDocumentToProvider&gt; the matching associations
     */
    public List<HRMDocumentToProvider> findByHrmDocumentId(Integer hrmDocumentId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hrmDocumentId);
        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }

    /**
     * Finds all provider associations for a document, excluding the system user ("-1").
     *
     * @param hrmDocumentId Integer the HRM document ID
     * @return List&lt;HRMDocumentToProvider&gt; the non-system-user associations
     */
    public List<HRMDocumentToProvider> findByHrmDocumentIdNoSystemUser(Integer hrmDocumentId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.providerNo != '-1'";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hrmDocumentId);
        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }

    /**
     * Finds the most recent provider association for a specific document and provider combination.
     *
     * @param hrmDocumentId Integer the HRM document ID
     * @param providerNo String the provider number
     * @return HRMDocumentToProvider the last matching association, or {@code null} if none found
     */
    public HRMDocumentToProvider findByHrmDocumentIdAndProviderNo(Integer hrmDocumentId, String providerNo) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.providerNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hrmDocumentId);
        query.setParameter(2, providerNo);
        try {
            List<HRMDocumentToProvider> results = query.getResultList();
            return results.get(results.size() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds all provider associations for a specific document and provider combination.
     *
     * @param hrmDocumentId Integer the HRM document ID
     * @param providerNo String the provider number
     * @return List&lt;HRMDocumentToProvider&gt; the matching associations
     */
    public List<HRMDocumentToProvider> findByHrmDocumentIdAndProviderNoList(Integer hrmDocumentId, String providerNo) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.providerNo=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hrmDocumentId);
        query.setParameter(2, providerNo);
        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }

    /**
     * Finds all signed-off provider associations for a given HRM document.
     *
     * @param hrmDocumentId Integer the HRM document ID
     * @return List&lt;HRMDocumentToProvider&gt; the signed-off associations
     */
    public List<HRMDocumentToProvider> findSignedByHrmDocumentId(Integer hrmDocumentId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.signedOff=1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, hrmDocumentId);
        @SuppressWarnings("unchecked")
        List<HRMDocumentToProvider> documentToProviders = query.getResultList();
        return documentToProviders;
    }

    /**
     * Returns the count of unsigned HRM documents for a given provider.
     *
     * @param providerNo String the provider number
     * @return Integer the count of unsigned documents
     */
    public Integer getCountByProviderNo(String providerNo) {
        String sql = "select count(*) from " + this.modelClass.getName() + " x where x.providerNo=?1 and x.signedOff=0";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        @SuppressWarnings("unchecked")
        Long result = (Long) query.getSingleResult();
        return result.intValue();
    }
}
