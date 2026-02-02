//CHECKSTYLE:OFF
/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.hospitalReportManager.model;

/**
 * Model object to hold HRM report display criteria and search parameters.
 * Used with ModelDriven pattern to eliminate boilerplate in action classes.
 */
public class HRMReportCriteria {

    private Integer id;               // reportId / document id
    private Integer segmentID;        // segment id
    private String providerNo;        // provider number
    private String searchProviderNo;  // search provider number
    private String status;            // report status
    private String demoName;          // patient / demo name
    private String duplicateLabIds;   // duplicate ids if any
    private Boolean listView;         // flag if rendering list view

    // Default constructor
    public HRMReportCriteria() {
    }

    // Getters and setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSegmentID() {
        return segmentID;
    }

    public void setSegmentID(Integer segmentID) {
        this.segmentID = segmentID;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getSearchProviderNo() {
        return searchProviderNo;
    }

    public void setSearchProviderNo(String searchProviderNo) {
        this.searchProviderNo = searchProviderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDemoName() {
        return demoName;
    }

    public void setDemoName(String demoName) {
        this.demoName = demoName;
    }

    public String getDuplicateLabIds() {
        return duplicateLabIds;
    }

    public void setDuplicateLabIds(String duplicateLabIds) {
        this.duplicateLabIds = duplicateLabIds;
    }

    public Boolean getListView() {
        return listView;
    }

    public void setListView(Boolean listView) {
        this.listView = listView;
    }
}