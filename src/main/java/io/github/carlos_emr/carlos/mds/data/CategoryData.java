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
 * Modifications by CARLOS Contributors, 2026.
 */

package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Operation;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Builds inbox category counts and patient summaries from typed DAO results. */
public class CategoryData {
    private final InboxCategoryDao dao = SpringUtils.getBean(InboxCategoryDao.class);
    private final InboxCategoryDao.Filter filter;
    private final boolean patientSearch;
    private final String abnormalStatus;
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

    /** @return int abnormal lab count from the most recent population */
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


    /**
     * Creates one inbox request's filters and patient-count accumulator.
     * @param patientLastName String optional surname fragment
     * @param patientFirstName String optional given-name fragment
     * @param patientHealthNumber String optional HIN fragment
     * @param patientSearch boolean whether to filter patients
     * @param providerSearch boolean whether to filter provider
     * @param searchProviderNo String provider identifier
     * @param status String optional routing status
     * @param abnormalStatus String optional normal/abnormal restriction
     * @param startDate String optional inclusive start date
     * @param endDate String optional inclusive end date
     */
    public CategoryData(String patientLastName, String patientFirstName, String patientHealthNumber,
                        boolean patientSearch, boolean providerSearch, String searchProviderNo,
                        String status, String abnormalStatus, String startDate, String endDate) {
        this.patientSearch = patientSearch;
        this.abnormalStatus = abnormalStatus;
        this.filter = new InboxCategoryDao.Filter(patientLastName, patientFirstName, patientHealthNumber,
                patientSearch, providerSearch, searchProviderNo, status, abnormalStatus, startDate, endDate);
        patients = new HashMap<>();
    }

    private int count(Operation operation, String demographicNo) throws SQLException {
        InboxCategoryDao.Counts result;
        try {
            result = dao.count(filter, operation, demographicNo);
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException
                 | org.springframework.transaction.TransactionException failure) {
            // Inbox controllers retain their checked-SQL failure path after the DAO migration.
            throw new SQLException("Could not read inbox category counts", failure);
        }
        for (InboxCategoryDao.PatientCount row : result.patients()) {
            if (operation == Operation.DOCUMENT_PATIENT) {
                PatientInfo patient = new PatientInfo(row.id(), row.firstName(), row.lastName());
                patient.setDocCount(row.documents());
                patients.put(row.id(), patient);
            } else {
                PatientInfo patient = patients.computeIfAbsent(row.id(),
                        id -> new PatientInfo(id, row.firstName(), row.lastName()));
                if (operation == Operation.LAB_PATIENT) patient.setLabCount(row.labs());
                if (operation == Operation.HRM_PATIENT) {
                    patient.setDocCount(patient.getDocCount() + row.documents());
                    patient.setHrmCount(row.hrm());
                }
            }
        }
        return result.total();
    }

    /** @return String current date-search preference */
    public String getDateSearchType() { return dao.getDateSearchType(); }
    /**
     * Counts unmatched labs.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getLabCountForUnmatched() throws SQLException { return count(Operation.LAB_UNMATCHED, null); }
    /**
     * Counts labs matching the requested abnormal flag.
     * @param abnormal boolean true for abnormal labs, false for normal labs
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getAbnormalCount(boolean abnormal) throws SQLException { return count(abnormal ? Operation.ABNORMAL : Operation.NORMAL, null); }
    /**
     * Counts unmatched documents.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getDocumentCountForUnmatched() throws SQLException { return count(Operation.DOCUMENT_UNMATCHED, null); }
    /**
     * Counts patient labs and accumulates lab summaries.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getLabCountForPatientSearch() throws SQLException { return count(Operation.LAB_PATIENT, null); }
    /**
     * Counts labs for the specified patient.
     * @param demographicNo String patient identifier
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getLabCountForDemographic(String demographicNo) throws SQLException { return count(Operation.LAB_DEMOGRAPHIC, demographicNo); }
    /**
     * Counts patient documents and replaces each returned patient entry before lab/HRM accumulation.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getDocumentCountForPatientSearch() throws SQLException { return count(Operation.DOCUMENT_PATIENT, null); }
    /**
     * Counts patient HRM reports and accumulates document and HRM summaries.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getHRMDocumentCountForPatient() throws SQLException { return count(Operation.HRM_PATIENT, null); }
    /**
     * Counts unmatched HRM reports.
     * @return int total matching items
     * @throws SQLException if the underlying count query fails
     */
    public int getHRMDocumentCountForUnmatched() throws SQLException { return count(Operation.HRM_UNMATCHED, null); }

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

        // Abnormal/normal filters are applied by the count queries. The unused
        // aggregate getters must not trigger an extra unscoped scan on every inbox load.

    }

    public Long getCategoryHash() {
        return Long.valueOf("" + (int) 'A' + totalNumDocs)
                + Long.valueOf("" + (int) 'D' + totalDocs)
                + Long.valueOf("" + (int) 'L' + totalLabs);
    }
}
