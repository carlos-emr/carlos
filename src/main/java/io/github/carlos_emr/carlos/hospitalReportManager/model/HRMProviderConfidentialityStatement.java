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
package io.github.carlos_emr.carlos.hospitalReportManager.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * JPA entity representing a provider's confidentiality statement that is
 * appended to printed HRM reports. Keyed by provider number.
 *
 * @since 2008-11-05
 */
@Entity
public class HRMProviderConfidentialityStatement extends AbstractModel<String> {

    @Id
    private String providerNo;

    private String statement;

    @Override
    public String getId() {
        return providerNo;
    }

    public void setId(String id) {
        this.providerNo = id;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

}
