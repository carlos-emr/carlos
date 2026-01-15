//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.mds.data;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.SystemPreferencesDao;
import ca.openosp.openo.commn.model.SystemPreferences;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.SpringUtils;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.Tuple;

public class CategoryData {

    private final SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);

	private final EntityManagerFactory entityManagerFactory = SpringUtils.getBean(EntityManagerFactory.class);
	private final EntityManager entityManager = entityManagerFactory.createEntityManager();


    private int totalDocs = 0;
    private int totalLabs = 0;
    private int unmatchedLabs = 0;
    private int unmatchedDocs = 0;
    private int totalNumDocs = 0;
    private int abnormalCount = 0;
    private int normalCount = 0;
    private int unmatchedHRMCount = 0;
    private int matchedHRMCount = 0;

    private HashMap<Integer, PatientInfo> patients;

    public int getTotalDocs() {
        return totalDocs;
    }

    public int getTotalLabs() {
        return totalLabs;
    }

    public int getUnmatchedLabs() {
        return unmatchedLabs;
    }

    public int getUnmatchedDocs() {
        return unmatchedDocs;
    }

	public int getUnmatchedHRMCount() {
		return unmatchedHRMCount;
	}

	public int getMatchedHRMCount() {
		return matchedHRMCount;
	}

    public int getTotalNumDocs() {
        return totalNumDocs;
    }

    public int getAbnormalCount() {
        return abnormalCount;
    }

    public int getNormalCount() {
        return normalCount;
    }

    public HashMap<Integer, PatientInfo> getPatients() {
        return patients;
    }

    public List<PatientInfo> getPatientList() {
        if (patients == null || patients.isEmpty()) {
            return Collections.emptyList();
        }
        List<PatientInfo> patientInfoList = new ArrayList<>(patients.values());
        Collections.sort(patientInfoList);
        return patientInfoList;
    }

    private String patientLastName;
    private String searchProviderNo;
    private String status;
	private String abnormalStatus;
    private String patientFirstName;
    private String patientHealthNumber;
    private boolean patientSearch;
    private boolean providerSearch;
    private String documentDateSql = "";
    private String documentAbnormalSql = "";
    private String documentJoinSql = "";
    private String labDateSql = "";
    private String labAbnormalSql = "";
    private String hrmDateSql = "";
    private String hrmProviderSql = "";
	private String hrmViewed = "";
	private String hrmSignedOff = "";

    public CategoryData(String patientLastName, String patientFirstName, String patientHealthNumber, boolean patientSearch,
                        boolean providerSearch, String searchProviderNo, String status, String abnormalStatus,
                        String startDate, String endDate) {

        this.patientLastName = patientLastName;
        this.searchProviderNo = searchProviderNo;
        this.status = status;
        this.patientFirstName = patientFirstName;
        this.patientHealthNumber = patientHealthNumber;
        this.patientSearch = patientSearch;
        this.providerSearch = providerSearch;
		this.abnormalStatus = abnormalStatus;

        SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
        String dateSearchType = "serviceObservation";

        SystemPreferences systemPreferences = systemPreferencesDao.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        if (systemPreferences != null) {
            if (systemPreferences.getValue() != null && !systemPreferences.getValue().isEmpty()) {
                dateSearchType = systemPreferences.getValue();
            }
        }

        if (startDate != null && !startDate.equals("")) {
            if (dateSearchType.equals("receivedCreated")) {
                documentDateSql += "AND doc.contentdatetime >= '" + startDate + " 00:00:00' ";
                labDateSql += "AND message.created >= '" + startDate + " 00:00:00' ";
            } else {
                documentDateSql += "AND doc.observationdate >= '" + startDate + " 00:00:00' ";
                labDateSql += "AND info.obr_date >= '" + startDate + " 00:00:00' ";
            }

            hrmDateSql += "AND h.timeReceived >= '" + startDate + " 00:00:00' ";
        }

        if (endDate != null && !endDate.equals("")) {
            if (dateSearchType.equals("receivedCreated")) {
                documentDateSql += "AND doc.contentdatetime <= '" + endDate + " 23:59:59' ";
                labDateSql += "AND message.created <= '" + endDate + " 23:59:59' ";
            } else {
                documentDateSql += "AND doc.observationdate <= '" + endDate + " 23:59:59' ";
                labDateSql += "AND info.obr_date <= '" + endDate + " 23:59:59' ";
            }

            hrmDateSql += "AND h.timeReceived <= '" + endDate + " 23:59:59' ";
        }

        if (abnormalStatus != null && !abnormalStatus.equals("all")) {
            if (abnormalStatus.equals("abnormalOnly")) {
                documentAbnormalSql = " AND doc.abnormal = TRUE ";
                labAbnormalSql = " AND info.result_status = 'A' ";
            } else if (abnormalStatus.equals("normalOnly")) {
                documentAbnormalSql = " AND (doc.abnormal = FALSE OR doc.abnormal IS NULL) ";
                labAbnormalSql = " AND (info.result_status IS NULL OR info.result_status != 'A') ";
            }
        }

        if (!documentDateSql.equals("") || !documentAbnormalSql.equals("")) {
            documentJoinSql = " LEFT JOIN document doc ON cd.document_no = doc.document_no";
        }

        if (providerSearch) {
            if (searchProviderNo.equals("0")) {
                hrmProviderSql += " AND hp.providerNo ='-1'";
            } else {
                hrmProviderSql += " AND hp.providerNo ='" + searchProviderNo + "' ";
            }
        }

		hrmViewed = " AND hp.viewed = 1 ";
		hrmSignedOff = " AND hp.signedOff = 0 ";
		if (status == null || status.equalsIgnoreCase("N")) {
			hrmViewed = "";
		} else if (status.equalsIgnoreCase("A") || status.equalsIgnoreCase("F")) {
			hrmSignedOff = " AND hp.signedOff = 1 ";
		} else if (status.isEmpty()) { // checks if status is an empty string
			hrmViewed = "";
			hrmSignedOff = "";
		}

        totalDocs = 0;
        totalLabs = 0;
        unmatchedLabs = 0;
        unmatchedDocs = 0;
        totalNumDocs = 0;
        abnormalCount = 0;
        normalCount = 0;

        patients = new HashMap<Integer, PatientInfo>();

    }

    public void populateCountsAndPatients() throws SQLException {

        // Retrieving documents and labs.
        totalDocs += getDocumentCountForPatientSearch();
        totalLabs += getLabCountForPatientSearch();

        //Checking for HRM counts for Logged in Doctor
		if (abnormalStatus == null || !abnormalStatus.equals("abnormalOnly")) {
			matchedHRMCount = getHRMDocumentCountForPatient();
		}

        //Adding matched HRM count to total docs
        totalDocs += matchedHRMCount;

        // If this is not a patient search, then we need to find the unmatched documents.
        if (!patientSearch) {
            unmatchedDocs += getDocumentCountForUnmatched();
            unmatchedLabs += getLabCountForUnmatched();

            //Unmatched Counts for HRM
			if (abnormalStatus == null || !abnormalStatus.equals("abnormalOnly")) {
				unmatchedHRMCount += getHRMDocumentCountForUnmatched();
			}

            //Adding Unmatched HRM to totalDocs
            totalDocs += unmatchedHRMCount;

            totalDocs += unmatchedDocs;
            totalLabs += unmatchedLabs;
        }

        // The total overall items is the sum of docs and labs.
        totalNumDocs = totalDocs + totalLabs;

        // Retrieving abnormal labs.
        abnormalCount = getAbnormalCount(true);

        // Cheaper to subtract abnormal from total to find the number of normal docs.
        normalCount = totalNumDocs - abnormalCount;
    }

    public String getDateSearchType() {
        SystemPreferences systemPreferences = systemPreferencesDao.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        if (systemPreferences != null) {
            if (systemPreferences.getValue() != null && !systemPreferences.getValue().isEmpty()) {
                return systemPreferences.getValue();
            }
        }

        return "serviceObservation";
    }

    public int getLabCountForUnmatched()
            throws SQLException {
        String dateSearchType = getDateSearchType();
        String sql = " SELECT HIGH_PRIORITY COUNT(DISTINCT accessionNum) as count "
                + " FROM providerLabRouting plr "
                + " LEFT JOIN patientLabRouting plr2 ON plr.lab_no = plr2.lab_no AND plr2.lab_type = 'HL7'"
                + " RIGHT JOIN hl7TextInfo info ON plr.lab_no = info.lab_no"
                + (dateSearchType.equals("receivedCreated") ? " RIGHT JOIN hl7TextMessage message ON plr.lab_no = message.lab_id" : "")
                + " WHERE plr.lab_type = 'HL7' "
                + (providerSearch ? " AND plr.provider_no = ? " : "")
                + " AND plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ? ")
                + labAbnormalSql
                + labDateSql
                + " AND (plr2.demographic_no IS NULL"
                + " OR plr2.demographic_no = '0')";


        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        
        int paramIndex = 1;
        if (providerSearch) ps.setString(paramIndex++, searchProviderNo);
        if (!"".equals(status)) ps.setString(paramIndex++, status);

        ResultSet rs = ps.executeQuery();

        return (rs.next() ? rs.getInt("count") : 0);
    }

    public int getAbnormalCount(boolean isAbnormal) throws SQLException {
        ResultSet rs;
        String sql;
        PreparedStatement ps = null;
        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        if (patientSearch) {
            sql = " SELECT HIGH_PRIORITY COUNT(1) as count "
                    + " FROM patientLabRouting cd, demographic d, providerLabRouting plr, hl7TextInfo info "
                    + " WHERE d.last_name" + (StringUtils.isEmpty(patientLastName) ? " IS NOT NULL " : " like ?  ")
                    + " 	AND d.first_name" + (StringUtils.isEmpty(patientFirstName) ? " IS NOT NULL " : " like ? ")
                    + " 	AND d.hin" + (StringUtils.isEmpty(patientHealthNumber) ? " IS NOT NULL " : " like ? ")
                    + " 	AND plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ? ")
                    + (providerSearch ? "AND plr.provider_no = ? " : "")
                    + " 	AND plr.lab_type = 'HL7' "
                    + " 	AND cd.lab_type = 'HL7' "
                    + " 	AND cd.lab_no = plr.lab_no "
                    + " 	AND cd.demographic_no = d.demographic_no "
                    + " 	AND info.lab_no = plr.lab_no "
                    + " 	AND result_status " + (isAbnormal ? "" : "!") + "= 'A' ";
            ps = c.prepareStatement(sql);

            int paramIndex = 1;
            if (!StringUtils.isEmpty(patientLastName)) ps.setString(paramIndex++, "%" + patientLastName + "%");
            if (!StringUtils.isEmpty(patientFirstName)) ps.setString(paramIndex++, "%" + patientFirstName + "%");
            if (!StringUtils.isEmpty(patientHealthNumber)) ps.setString(paramIndex++, "%" + patientHealthNumber + "%");
            if (!"".equals(status)) ps.setString(paramIndex++, status);
            if (providerSearch) ps.setString(paramIndex++, searchProviderNo);
        } else if (providerSearch || !"".equals(status)) { // providerSearch
            sql = "SELECT HIGH_PRIORITY COUNT(1) as count "
                    + " FROM providerLabRouting plr, hl7TextInfo info "
                    + " WHERE plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ?")
                    + (providerSearch ? " AND plr.provider_no = ? " : " ")
                    + " AND plr.lab_type = 'HL7'  "
                    + " AND info.lab_no = plr.lab_no"
                    + " AND result_status " + (isAbnormal ? "" : "!") + "= 'A' ";
            ps = c.prepareStatement(sql);

            int paramIndex = 1;
            if (!"".equals(status)) ps.setString(paramIndex++, status);
            if (providerSearch) ps.setString(paramIndex++, searchProviderNo);
        } else {
            sql = " SELECT HIGH_PRIORITY COUNT(1) as count "
                    + " FROM hl7TextInfo info "
                    + " WHERE result_status " + (isAbnormal ? "" : "!") + "= 'A' ";
            ps = c.prepareStatement(sql);
        }
        rs = ps.executeQuery();
        return (rs.next() ? rs.getInt("count") : 0);
    }

    public int getDocumentCountForUnmatched()
            throws SQLException {
        String sql = " SELECT HIGH_PRIORITY COUNT(1) as count "
                + " FROM ctl_document cd "
                + "LEFT JOIN providerLabRouting plr ON plr.lab_no = cd.document_no"
                + documentJoinSql
                + " WHERE plr.lab_type = 'DOC' "
                + " AND plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ? ")
                + (providerSearch ? " AND plr.provider_no = ? " : "")
                + " AND 	cd.module_id = -1 "
                + documentAbnormalSql
                + documentDateSql;
        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        PreparedStatement ps = c.prepareStatement(sql);

        int paramIndex = 1;
        if (!"".equals(status)) ps.setString(paramIndex++, status);
        if (providerSearch) ps.setString(paramIndex++, searchProviderNo);
        ResultSet rs = ps.executeQuery();

        return (rs.next() ? rs.getInt("count") : 0);
    }

    public int getLabCountForPatientSearch() throws SQLException {
        PatientInfo info;
        String dateSearchType = getDateSearchType();
        String sql = " SELECT HIGH_PRIORITY d.demographic_no, d.last_name, d.first_name, COUNT(*) as count "
                + " FROM patientLabRouting cd"
                + " LEFT JOIN demographic d ON cd.demographic_no = d.demographic_no"
                + " LEFT JOIN providerLabRouting plr ON cd.lab_no = plr.lab_no"
                + " LEFT JOIN hl7TextInfo info ON cd.lab_no = info.lab_no"
                + (dateSearchType.equals("receivedCreated") ? " LEFT JOIN hl7TextMessage message ON cd.lab_no = message.lab_id" : "")
                + " WHERE   d.last_name" + (StringUtils.isEmpty(patientLastName) ? " IS NOT NULL " : "  like ? ")
                + " 	AND d.first_name" + (StringUtils.isEmpty(patientFirstName) ? " IS NOT NULL " : " like ? ")
                + " 	AND d.hin" + (StringUtils.isEmpty(patientHealthNumber) ? " IS NOT NULL " : " like ? ")
                + " 	AND plr.lab_type = 'HL7' "
                + " 	AND cd.lab_type = 'HL7' "
                + " 	AND plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ? ")
                + (dateSearchType.equals("receivedCreated") ? " AND message.lab_id IS NOT NULL " : " AND info.lab_no IS NOT NULL ")
                + (providerSearch ? " AND plr.provider_no = ? " : "")
                + labAbnormalSql
                + labDateSql
                + " GROUP BY demographic_no, info.accessionNum ";

        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        PreparedStatement ps = c.prepareStatement(sql);

        int paramIndex = 1;
        if (!StringUtils.isEmpty(patientLastName)) ps.setString(paramIndex++, "%" + patientLastName + "%");
        if (!StringUtils.isEmpty(patientFirstName)) ps.setString(paramIndex++, "%" + patientFirstName + "%");
        if (!StringUtils.isEmpty(patientHealthNumber)) ps.setString(paramIndex++, "%" + patientHealthNumber + "%");
        if (!"".equals(status)) ps.setString(paramIndex++, status);
        if (providerSearch) ps.setString(paramIndex++, searchProviderNo);
        ResultSet rs = ps.executeQuery();

        int totalCount = 0;
        while (rs.next()) {
            int id = rs.getInt("demographic_no");
			int count = rs.getInt("count");
            // Updating patient info if it already exists.
            if (patients.containsKey(id)) {
                info = patients.get(id);
        		info.setLabCount(count);
            }
            // Otherwise adding a new patient record.
            else {
                info = new PatientInfo(id, rs.getString("first_name"), rs.getString("last_name"));
        		info.setLabCount(count);
                patients.put(info.getId(), info);
            }
        	totalCount += count;
        }
        return totalCount;
    }

    public int getLabCountForDemographic(String demographicNo) throws SQLException {
        if (demographicNo == null || demographicNo.trim().isEmpty()) {
            throw new IllegalArgumentException("demographicNo cannot be null or empty");
        }
    
        // Build SQL with placeholders
        StringBuilder sql = new StringBuilder();
        sql.append(" SELECT HIGH_PRIORITY d.demographic_no, last_name, first_name, COUNT(1) as count ");
        sql.append(" FROM patientLabRouting cd, demographic d, providerLabRouting plr ");
        sql.append(" WHERE d.demographic_no = ? ");
        sql.append(" AND cd.demographic_no = d.demographic_no ");
        sql.append(" AND cd.lab_no = plr.lab_no ");
        sql.append(" AND plr.lab_type = 'HL7' ");
        sql.append(" AND cd.lab_type = 'HL7' ");
        
        if ("".equals(status)) {
            sql.append(" AND plr.status IS NOT NULL ");
        } else {
            sql.append(" AND plr.status = ? ");
        }
        
        if (providerSearch) {
            sql.append(" AND plr.provider_no = ? ");
        }
        
        sql.append(" GROUP BY demographic_no ");
        
        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        
        // Try-with-resources automatically closes resources
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            ps.setString(paramIndex++, demographicNo);
            
            if (!"".equals(status)) {
                ps.setString(paramIndex++, status);
            }
            
            if (providerSearch) {
                ps.setString(paramIndex++, searchProviderNo);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                return (rs.next() ? rs.getInt("count") : 0);
            }
        }
    }

    /*
     * This will return the total documents found for this patients.
     * it will also add to the patients map (demographicNo, PatientInfo) with a document count for the patient.
     *
     */
    public int getDocumentCountForPatientSearch() throws SQLException {
        PatientInfo info;

        String sql = " SELECT HIGH_PRIORITY demographic_no, last_name, first_name, COUNT( distinct cd.document_no) as count "
                + " FROM ctl_document cd "
                + "LEFT JOIN demographic d  ON cd.module_id = d.demographic_no "
                + "LEFT JOIN providerLabRouting plr ON cd.document_no = plr.lab_no "
                + documentJoinSql
                + " WHERE   d.last_name" + (StringUtils.isEmpty(patientLastName) ? " IS NOT NULL " : " like ?  ")
                + " 	AND d.hin" + (StringUtils.isEmpty(patientHealthNumber) ? " IS NOT NULL " : " like ? ")
                + " 	AND d.first_name" + (StringUtils.isEmpty(patientFirstName) ? " IS NOT NULL " : " like ? ")
                + " 	AND plr.lab_type = 'DOC' "
                + " 	AND plr.status " + ("".equals(status) ? " IS NOT NULL " : " = ? ")
                + (providerSearch ? "AND plr.provider_no = ? " : "")
                + documentAbnormalSql
                + documentDateSql
                + " GROUP BY demographic_no ";
        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        PreparedStatement ps = c.prepareStatement(sql);

        int paramIndex = 1;
        if (!StringUtils.isEmpty(patientLastName)) ps.setString(paramIndex++, "%" + patientLastName + "%");
        if (!StringUtils.isEmpty(patientHealthNumber)) ps.setString(paramIndex++, "%" + patientHealthNumber + "%");
        if (!StringUtils.isEmpty(patientFirstName)) ps.setString(paramIndex++, "%" + patientFirstName + "%");
        if (!"".equals(status)) ps.setString(paramIndex++, status);
        if (providerSearch) ps.setString(paramIndex++, searchProviderNo);

        ResultSet rs = ps.executeQuery();

        int count = 0;
        while (rs.next()) {
            info = new PatientInfo(rs.getInt("demographic_no"), rs.getString("first_name"), rs.getString("last_name"));
            info.setDocCount(rs.getInt("count"));
            patients.put(info.getId(), info);
            count += info.getDocCount();
        }

        return count;
    }

    public int getHRMDocumentCountForPatient() throws SQLException {
        int count = 0;
        PatientInfo info;

		StringBuilder sql = new StringBuilder();
        sql.append("SELECT HIGH_PRIORITY d.demographic_no, d.first_name, d.last_name, COUNT(DISTINCT h.id) AS count ")
           .append(" FROM HRMDocumentToDemographic hd ")
           .append(" LEFT JOIN HRMDocumentToProvider hp ON hd.hrmDocumentId = hp.hrmDocumentId ")
           .append(" LEFT JOIN HRMDocument h ON hd.hrmDocumentId = h.id ")
           .append(" JOIN demographic d ON hd.demographicNo = d.demographic_no ")
           .append(" WHERE 1=1 ")
           .append(" AND d.last_name ").append(StringUtils.isNotEmpty(patientLastName) ? "LIKE :patientLastName " : "IS NOT NULL ")
           .append(" AND d.hin ").append(StringUtils.isNotEmpty(patientHealthNumber) ? "LIKE :patientHealthNumber " : "IS NOT NULL ")
           .append(" AND d.first_name ").append(StringUtils.isNotEmpty(patientFirstName) ? "LIKE :patientFirstName " : "IS NOT NULL ")
           .append(hrmViewed).append(hrmSignedOff).append(hrmDateSql).append(hrmProviderSql)
           .append(" GROUP BY d.demographic_no ");

        Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);

        // Set parameters only if they are not empty
        if (StringUtils.isNotEmpty(patientLastName)) {
            query.setParameter("patientLastName", "%" + patientLastName + "%");
        }
        if (StringUtils.isNotEmpty(patientHealthNumber)) {
            query.setParameter("patientHealthNumber", "%" + patientHealthNumber + "%");
        }
        if (StringUtils.isNotEmpty(patientFirstName)) {
            query.setParameter("patientFirstName", "%" + patientFirstName + "%");
        }

        // Executing the query and processing the results
		List<Tuple> results = query.getResultList();

		for (Tuple result : results) {
			Integer hrmCount = result.get("count", BigInteger.class).intValue(); // Extracting count as Integer
			Integer id = result.get("demographic_no", Integer.class); // Extracting demographicNo as Integer

			// Updating patient info if it already exists
            if (patients.containsKey(id)) {
                info = patients.get(id);
                info.setDocCount(info.getDocCount() + hrmCount);
				info.setHrmCount(hrmCount);
			} else {
				// Otherwise, adding a new patient record
				info = new PatientInfo(id, result.get("first_name", String.class), result.get("last_name", String.class));
                info.setDocCount(hrmCount);
				info.setHrmCount(hrmCount);
                patients.put(info.getId(), info);
            }

            count += hrmCount;
        }

        return count;
    }

    public int getHRMDocumentCountForUnmatched() throws SQLException {
        int count = 0;

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT HIGH_PRIORITY COUNT(DISTINCT h.id) AS count ")
			.append(" FROM HRMDocumentToProvider hp ")
			.append(" LEFT JOIN HRMDocument h ON h.id = hp.hrmDocumentId ")
			.append(" LEFT JOIN HRMDocumentToDemographic hd ON hd.hrmDocumentId = hp.hrmDocumentId ")
			.append(" WHERE hd.hrmDocumentId IS NULL ")
			.append(hrmViewed)
			.append(hrmSignedOff)
			.append(hrmDateSql)
			.append(hrmProviderSql);

		// Create a native query using the entity manager
		Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);

		// Execute the query and retrieve the results
		List<Tuple> results = query.getResultList();

		// Process the results
		for (Tuple result : results) {
			Integer hrmCount = result.get("count", BigInteger.class).intValue(); // Extracting count as Integer
			count += hrmCount; // Accumulate the total count
        }

        return count;
    }

    public Long getCategoryHash() {
        return Long.valueOf("" + (int) 'A' + totalNumDocs)
                + Long.valueOf("" + (int) 'D' + totalDocs)
                + Long.valueOf("" + (int) 'L' + totalLabs);
    }
}
