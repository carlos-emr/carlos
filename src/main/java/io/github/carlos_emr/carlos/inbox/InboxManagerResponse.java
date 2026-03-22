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
package io.github.carlos_emr.carlos.inbox;

import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

/**
 * Response object for inbox manager queries containing paginated inbox results.
 * Holds document metadata, patient identification mappings, lab result data,
 * document type classifications, acknowledgement statuses, and summary counts
 * for both normal and abnormal lab results.
 *
 * @since 2026-03-17
 */
public class InboxManagerResponse {
    private Integer pageNum;
    private Hashtable docType;
    private Hashtable patientDocs;
    private String providerNo;
    private String searchProviderNo;
    private Hashtable patientIdNames;
    private Hashtable docStatus;
    private String patientIdStr;
    private Hashtable<String, List<String>> typeDocLab;
    private Integer demographicNo;
    private String ackStatus;
    private List<LabResultData> labdocs;
    private Hashtable patientNumDoc;
    private Integer totalDocs;
    private Integer totalHl7;
    private List<String> normals;
    private List<String> abnormals;
    private Integer totalNumDocs;
    private String patientIdNamesStr;
    private Date oldestLab;

    /**
     * Returns the current page number in the paginated result set.
     *
     * @return Integer the page number
     */
    public Integer getPageNum() {
        return pageNum;
    }

    /**
     * Sets the current page number in the paginated result set.
     *
     * @param pageNum Integer the page number
     */
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * Returns the mapping of document identifiers to their document types
     * (e.g., lab, document, HRM).
     *
     * @return Hashtable the document type mappings
     */
    public Hashtable getDocType() {
        return docType;
    }

    /**
     * Sets the mapping of document identifiers to their document types.
     *
     * @param docType Hashtable the document type mappings
     */
    public void setDocType(Hashtable docType) {
        this.docType = docType;
    }

    /**
     * Returns the mapping of patient identifiers to their associated documents.
     *
     * @return Hashtable the patient-to-documents mappings
     */
    public Hashtable getPatientDocs() {
        return patientDocs;
    }

    /**
     * Sets the mapping of patient identifiers to their associated documents.
     *
     * @param patientDocs Hashtable the patient-to-documents mappings
     */
    public void setPatientDocs(Hashtable patientDocs) {
        this.patientDocs = patientDocs;
    }

    /**
     * Returns the provider number of the inbox owner.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return providerNo;
    }

    /**
     * Sets the provider number of the inbox owner.
     *
     * @param providerNo String the provider number
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Returns the provider number that was used for the search query.
     *
     * @return String the search provider number
     */
    public String getSearchProviderNo() {
        return searchProviderNo;
    }

    /**
     * Sets the provider number that was used for the search query.
     *
     * @param searchProviderNo String the search provider number
     */
    public void setSearchProviderNo(String searchProviderNo) {
        this.searchProviderNo = searchProviderNo;
    }

    /**
     * Returns the mapping of patient identifiers to patient display names.
     *
     * @return Hashtable the patient ID-to-name mappings
     */
    public Hashtable getPatientIdNames() {
        return patientIdNames;
    }

    /**
     * Sets the mapping of patient identifiers to patient display names.
     *
     * @param patientIdNames Hashtable the patient ID-to-name mappings
     */
    public void setPatientIdNames(Hashtable patientIdNames) {
        this.patientIdNames = patientIdNames;
    }

    /**
     * Returns the mapping of document identifiers to their acknowledgement statuses.
     *
     * @return Hashtable the document status mappings
     */
    public Hashtable getDocStatus() {
        return docStatus;
    }

    /**
     * Sets the mapping of document identifiers to their acknowledgement statuses.
     *
     * @param docStatus Hashtable the document status mappings
     */
    public void setDocStatus(Hashtable docStatus) {
        this.docStatus = docStatus;
    }

    /**
     * Returns a string representation of patient identifiers,
     * typically a comma-separated list used for display or further queries.
     *
     * @return String the patient ID string
     */
    public String getPatientIdStr() {
        return patientIdStr;
    }

    /**
     * Sets the string representation of patient identifiers.
     *
     * @param patientIdStr String the patient ID string
     */
    public void setPatientIdStr(String patientIdStr) {
        this.patientIdStr = patientIdStr;
    }

    /**
     * Returns the mapping of document type names to lists of document/lab identifiers
     * categorized by type (e.g., "DOC" to document IDs, "HL7" to lab IDs).
     *
     * @return Hashtable&lt;String, List&lt;String&gt;&gt; the type-to-document/lab ID mappings
     */
    public Hashtable<String, List<String>> getTypeDocLab() {
        return typeDocLab;
    }

    /**
     * Sets the mapping of document type names to lists of document/lab identifiers.
     *
     * @param typeDocLab Hashtable&lt;String, List&lt;String&gt;&gt; the type-to-document/lab ID mappings
     */
    public void setTypeDocLab(Hashtable<String, List<String>> typeDocLab) {
        this.typeDocLab = typeDocLab;
    }

    /**
     * Returns the demographic (patient) number associated with this response,
     * when results are filtered for a specific patient.
     *
     * @return Integer the demographic number, or {@code null} if not patient-specific
     */
    public Integer getDemographicNo() {
        return demographicNo;
    }

    /**
     * Sets the demographic (patient) number associated with this response.
     *
     * @param demographicNo Integer the demographic number
     */
    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Returns the acknowledgement status filter that was applied to produce these results.
     *
     * @return String the acknowledgement status
     */
    public String getAckStatus() {
        return ackStatus;
    }

    /**
     * Sets the acknowledgement status filter that was applied to produce these results.
     *
     * @param ackStatus String the acknowledgement status
     */
    public void setAckStatus(String ackStatus) {
        this.ackStatus = ackStatus;
    }

    /**
     * Returns the list of lab result data objects representing lab documents in the inbox.
     *
     * @return List&lt;LabResultData&gt; the lab result documents
     */
    public List<LabResultData> getLabdocs() {
        return labdocs;
    }

    /**
     * Sets the list of lab result data objects.
     *
     * @param labdocs List&lt;LabResultData&gt; the lab result documents
     */
    public void setLabdocs(List<LabResultData> labdocs) {
        this.labdocs = labdocs;
    }

    /**
     * Returns the mapping of patient identifiers to their document counts.
     *
     * @return Hashtable the patient-to-document-count mappings
     */
    public Hashtable getPatientNumDoc() {
        return patientNumDoc;
    }

    /**
     * Sets the mapping of patient identifiers to their document counts.
     *
     * @param patientNumDoc Hashtable the patient-to-document-count mappings
     */
    public void setPatientNumDoc(Hashtable patientNumDoc) {
        this.patientNumDoc = patientNumDoc;
    }

    /**
     * Returns the total number of non-HL7 documents in the result set.
     *
     * @return Integer the total document count
     */
    public Integer getTotalDocs() {
        return totalDocs;
    }

    /**
     * Sets the total number of non-HL7 documents in the result set.
     *
     * @param totalDocs Integer the total document count
     */
    public void setTotalDocs(Integer totalDocs) {
        this.totalDocs = totalDocs;
    }

    /**
     * Returns the total number of HL7 lab result messages in the result set.
     *
     * @return Integer the total HL7 lab count
     */
    public Integer getTotalHl7() {
        return totalHl7;
    }

    /**
     * Sets the total number of HL7 lab result messages in the result set.
     *
     * @param totalHl7 Integer the total HL7 lab count
     */
    public void setTotalHl7(Integer totalHl7) {
        this.totalHl7 = totalHl7;
    }

    /**
     * Returns the list of identifiers for lab results with normal values.
     *
     * @return List&lt;String&gt; the normal lab result identifiers
     */
    public List<String> getNormals() {
        return normals;
    }

    /**
     * Sets the list of identifiers for lab results with normal values.
     *
     * @param normals List&lt;String&gt; the normal lab result identifiers
     */
    public void setNormals(List<String> normals) {
        this.normals = normals;
    }

    /**
     * Returns the list of identifiers for lab results with abnormal values.
     *
     * @return List&lt;String&gt; the abnormal lab result identifiers
     */
    public List<String> getAbnormals() {
        return abnormals;
    }

    /**
     * Sets the list of identifiers for lab results with abnormal values.
     *
     * @param abnormals List&lt;String&gt; the abnormal lab result identifiers
     */
    public void setAbnormals(List<String> abnormals) {
        this.abnormals = abnormals;
    }

    /**
     * Returns the combined total number of all documents (HL7 and non-HL7) in the result set.
     *
     * @return Integer the total number of all documents
     */
    public Integer getTotalNumDocs() {
        return totalNumDocs;
    }

    /**
     * Sets the combined total number of all documents.
     *
     * @param totalNumDocs Integer the total number of all documents
     */
    public void setTotalNumDocs(Integer totalNumDocs) {
        this.totalNumDocs = totalNumDocs;
    }

    /**
     * Returns a string representation of patient ID-to-name mappings,
     * typically formatted for use in the UI layer.
     *
     * @return String the patient ID-to-name string
     */
    public String getPatientIdNamesStr() {
        return patientIdNamesStr;
    }

    /**
     * Sets the string representation of patient ID-to-name mappings.
     *
     * @param patientIdNamesStr String the patient ID-to-name string
     */
    public void setPatientIdNamesStr(String patientIdNamesStr) {
        this.patientIdNamesStr = patientIdNamesStr;
    }

    /**
     * Returns the date of the oldest lab result in the result set,
     * used for date range display and pagination boundary tracking.
     *
     * @return Date the oldest lab date, or {@code null} if no labs are present
     */
    public Date getOldestLab() {
        return oldestLab;
    }

    /**
     * Sets the date of the oldest lab result in the result set.
     *
     * @param oldestLab Date the oldest lab date
     */
    public void setOldestLab(Date oldestLab) {
        this.oldestLab = oldestLab;
    }
}