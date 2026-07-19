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

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

@Entity
public class HRMDocumentSubClass extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer hrmDocumentId;
    private String subClass;
    private String subClassMnemonic;
    private String subClassDescription;
    private Date subClassDateTime;
    private boolean isActive;
    private String sendingFacilityId;


    @Override
    public Integer getId() {
        return id;
    }

    public Integer getHrmDocumentId() {
        return hrmDocumentId;
    }

    public void setHrmDocumentId(Integer hrmDocumentId) {
        this.hrmDocumentId = hrmDocumentId;
    }

    public String getSubClass() {
        return subClass;
    }

    public void setSubClass(String subClass) {
        this.subClass = subClass;
    }

    public String getSubClassMnemonic() {
        return subClassMnemonic;
    }

    public void setSubClassMnemonic(String subClassMnemonic) {
        this.subClassMnemonic = subClassMnemonic;
    }

    public String getSubClassDescription() {
        return subClassDescription;
    }

    public void setSubClassDescription(String subClassDescription) {
        this.subClassDescription = subClassDescription;
    }

    public Date getSubClassDateTime() {
        return subClassDateTime;
    }

    public void setSubClassDateTime(Date subClassDateTime) {
        this.subClassDateTime = subClassDateTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public String getSendingFacilityId() {
        return sendingFacilityId;
    }

    public void setSendingFacilityId(String sendingFacilityId) {
        this.sendingFacilityId = sendingFacilityId;
    }

}
