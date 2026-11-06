// SPDX-License-Identifier: GPL-2.0-or-later
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
        InboxCategoryDao.Counts result = dao.count(filter, operation, demographicNo);
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

    public String getDateSearchType() { return dao.getDateSearchType(); }
    public int getLabCountForUnmatched() throws SQLException { return count(Operation.LAB_UNMATCHED, null); }
    public int getAbnormalCount(boolean abnormal) throws SQLException { return count(abnormal ? Operation.ABNORMAL : Operation.NORMAL, null); }
    public int getDocumentCountForUnmatched() throws SQLException { return count(Operation.DOCUMENT_UNMATCHED, null); }
    public int getLabCountForPatientSearch() throws SQLException { return count(Operation.LAB_PATIENT, null); }
    public int getLabCountForDemographic(String demographicNo) throws SQLException { return count(Operation.LAB_DEMOGRAPHIC, demographicNo); }
    public int getDocumentCountForPatientSearch() throws SQLException { return count(Operation.DOCUMENT_PATIENT, null); }
    public int getHRMDocumentCountForPatient() throws SQLException { return count(Operation.HRM_PATIENT, null); }
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

        // Retrieving abnormal labs.
        abnormalCount = getAbnormalCount(true);

        // Cheaper to subtract abnormal from total to find the number of normal docs.
        normalCount = totalNumDocs - abnormalCount;
    }

    public Long getCategoryHash() {
        return Long.valueOf("" + (int) 'A' + totalNumDocs)
                + Long.valueOf("" + (int) 'D' + totalDocs)
                + Long.valueOf("" + (int) 'L' + totalLabs);
    }
}
