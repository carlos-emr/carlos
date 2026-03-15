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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

@Entity
public class HRMCategory extends AbstractModel<Integer> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String categoryName;
    private String subClassNameMnemonic;
    private String sendingFacilityId;

    public HRMCategory() {

    }

    @Override
    public Integer getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSubClassNameMnemonic() {
        return (subClassNameMnemonic);
    }

    public void setSubClassNameMnemonic(String subClassNameMnemonic) {
        this.subClassNameMnemonic = subClassNameMnemonic;
    }

    public String getSendingFacilityId() {
        return sendingFacilityId;
    }

    public void setSendingFacilityId(String sendingFacilityId) {
        this.sendingFacilityId = sendingFacilityId;
    }

}
