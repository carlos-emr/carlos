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

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * JPA entity representing the association between an HRM document and a healthcare
 * provider, tracking sign-off status, sign-off timestamp, and viewed status.
 *
 * <p>A provider number of "-1" indicates an unclaimed/unmatched document.</p>
 *
 * @since 2008-11-05
 */
@Entity
public class HRMDocumentToProvider extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String providerNo;
    private Integer hrmDocumentId;
    private Integer signedOff = 0;
    private Date signedOffTimestamp;
    private Integer viewed = 0;

    @Override
    public Integer getId() {
        return id;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getSignedOff() {
        return signedOff;
    }

    public void setSignedOff(Integer signedOff) {
        this.signedOff = signedOff;
    }

    public Date getSignedOffTimestamp() {
        return signedOffTimestamp;
    }

    public void setSignedOffTimestamp(Date signedOffTimestamp) {
        this.signedOffTimestamp = signedOffTimestamp;
    }

    public Integer getHrmDocumentId() {
        return hrmDocumentId;
    }

    public void setHrmDocumentId(Integer hrmDocumentId) {
        this.hrmDocumentId = hrmDocumentId;
    }

    public Integer getViewed() {
        return viewed;
    }

    public void setViewed(Integer viewed) {
        this.viewed = viewed;
    }


}
