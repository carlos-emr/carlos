/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.model.inbox;

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * An abstract class to collect and pass inbox query parameters
 * Extending classes should translate provided parameters to the valid formats used by the inbox repository
 * <p>
 * All member variables are by default set to the most broad search criteria
 */
public class InboxQueryParameters {
    public enum MatchedStatus {
        ALL, MATCHED, NOT_MATCHED
    }

    private Provider loggedInProvider;
    private String providerNumber = "";
    private String firstName = "";
    private String lastName = "";
    private String hin = "";
    private String startDate = "";
    private String endDate = "";
    private String status = "";
    private String abnormalStatus = "";
    private MatchedStatus matchedStatus = MatchedStatus.ALL;
    private String sortBy = "";
    private String sortOrder = "";
    private Integer page = 0;
    private Integer resultsPerPage = 40;
    private Boolean showDocuments = true;
    private Boolean showLabs = true;
    private Boolean showHrm = true;
    private Boolean getCounts = false;
    private Boolean getDemographicCounts = false;
    private Boolean documentShowDescription = false;

    public InboxQueryParameters(Provider loggedInProvider) {
        this.loggedInProvider = loggedInProvider;
    }

    public Provider getLoggedInProvider() {
        return loggedInProvider;
    }

    public void setLoggedInProvider(Provider loggedInProvider) {
        this.loggedInProvider = loggedInProvider;
    }


    public String getProviderNumber() {
        return providerNumber;
    }

    public void setProviderNumber(String providerNumber) {
        this.providerNumber = providerNumber;
    }

    public InboxQueryParameters whereProviderNumber(String providerNumber) {
        setProviderNumber(providerNumber);
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public InboxQueryParameters whereFirstName(String firstName) {
        setFirstName(firstName);
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public InboxQueryParameters whereLastName(String lastName) {
        setLastName(lastName);
        return this;
    }

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public InboxQueryParameters whereHin(String hin) {
        setHin(hin);
        return this;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public InboxQueryParameters whereStartDate(String startDate) {
        setStartDate(startDate);
        return this;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public InboxQueryParameters whereEndDate(String endDate) {
        setEndDate(endDate);
        return this;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public InboxQueryParameters whereStatus(String status) {
        setStatus(status);
        return this;
    }

    public String getAbnormalStatus() {
        return abnormalStatus;
    }

    public void setAbnormalStatus(String abnormalStatus) {
        this.abnormalStatus = abnormalStatus;
    }

    public InboxQueryParameters whereAbnormalStatus(String abnormalStatus) {
        setAbnormalStatus(abnormalStatus);
        return this;
    }

    public MatchedStatus getMatchedStatus() {
        return matchedStatus;
    }

    public void setMatchedStatus(MatchedStatus matchedStatus) {
        this.matchedStatus = matchedStatus;
    }

    public InboxQueryParameters whereMatchedStatus(MatchedStatus matchedStatus) {
        setMatchedStatus(matchedStatus);
        return this;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public InboxQueryParameters whereSortBy(String sortBy) {
        setSortBy(sortBy);
        return this;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public InboxQueryParameters whereSortOrder(String sortOrder) {
        setSortOrder(sortOrder);
        return this;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public InboxQueryParameters wherePage(Integer page) {
        setPage(page);
        return this;
    }

    public Integer getResultsPerPage() {
        return resultsPerPage;
    }

    public void setResultsPerPage(Integer resultsPerPage) {
        this.resultsPerPage = resultsPerPage;
    }

    public InboxQueryParameters whereResultsPerPage(Integer resultsPerPage) {
        setResultsPerPage(resultsPerPage);
        return this;
    }

    public Boolean getShowDocuments() {
        return showDocuments;
    }

    public void setShowDocuments(Boolean showDocuments) {
        this.showDocuments = showDocuments;
    }

    public InboxQueryParameters whereShowDocuments(Boolean showDocuments) {
        setShowDocuments(showDocuments);
        return this;
    }

    public Boolean getShowLabs() {
        return showLabs;
    }

    public void setShowLabs(Boolean showLabs) {
        this.showLabs = showLabs;
    }

    public InboxQueryParameters whereShowLabs(Boolean showLabs) {
        setShowLabs(showLabs);
        return this;
    }

    public Boolean getShowHrm() {
        return showHrm;
    }

    public void setShowHrm(Boolean showHrm) {
        this.showHrm = showHrm;
    }

    public InboxQueryParameters whereShowHrm(Boolean showHrm) {
        setShowHrm(showHrm);
        return this;
    }

    public Boolean getGetCounts() {
        return getCounts;
    }

    public void setGetCounts(Boolean getCounts) {
        this.getCounts = getCounts;
    }

    public InboxQueryParameters whereGetCounts(Boolean getCounts) {
        setGetCounts(getCounts);
        return this;
    }

    public Boolean getGetDemographicCounts() {
        return getDemographicCounts;
    }

    public void setGetDemographicCounts(Boolean getDemographicCounts) {
        this.getDemographicCounts = getDemographicCounts;
    }

    public InboxQueryParameters whereGetDemographicCounts(Boolean getDemographicCounts) {
        setGetDemographicCounts(getDemographicCounts);
        return this;
    }

    public Boolean getDocumentShowDescription() {
        return documentShowDescription;
    }

    public void setDocumentShowDescription(Boolean documentShowDescription) {
        this.documentShowDescription = documentShowDescription;
    }

    public InboxQueryParameters whereDocumentShowDescription(Boolean documentShowDescription) {
        setDocumentShowDescription(documentShowDescription);
        return this;
    }
}
