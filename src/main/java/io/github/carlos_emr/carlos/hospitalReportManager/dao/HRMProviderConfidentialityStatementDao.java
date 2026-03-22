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


import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMProviderConfidentialityStatement;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMProviderConfidentialityStatement} entities,
 * managing provider-specific confidentiality statements appended to printed HRM reports.
 *
 * @see HRMProviderConfidentialityStatement
 * @since 2008-11-05
 */
@Repository
public class HRMProviderConfidentialityStatementDao extends AbstractDaoImpl<HRMProviderConfidentialityStatement> {

    public HRMProviderConfidentialityStatementDao() {
        super(HRMProviderConfidentialityStatement.class);
    }

    /**
     * Returns the confidentiality statement text for a given provider.
     *
     * @param providerNo String the provider number
     * @return String the statement text, or empty string if no statement exists
     */
    public String getConfidentialityStatementForProvider(String providerNo) {
        String sql = "select x.statement from " + this.modelClass.getName() + " x where x.providerNo=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        try {
            return (String) query.getSingleResult();
        } catch (Exception e) {
            // No statement for this providers
            return "";
        }
    }

    /**
     * Finds the confidentiality statement entity for a given provider.
     *
     * @param providerNo String the provider number
     * @return HRMProviderConfidentialityStatement the statement entity, or {@code null} if not found
     */
    public HRMProviderConfidentialityStatement findByProvider(String providerNo) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.providerNo=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        return this.getSingleResultOrNull(query);
    }


}
