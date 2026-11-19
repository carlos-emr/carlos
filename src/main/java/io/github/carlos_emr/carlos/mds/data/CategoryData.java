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
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.DateSearchType;
import io.github.carlos_emr.carlos.commn.dao.InboxCategoryDao.Operation;
import io.github.carlos_emr.carlos.commn.service.InboxDateSearchTypeResolver;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds inbox category counts and patient summaries from typed DAO results.
 *
 * <p>The {@code inboxDateSearchType} preference is read once per population (issue #5772) and
 * that snapshot is used by every category count of the population and by the lazy abnormal count
 * read after it, so the normal count ({@code totalLabs - abnormal}) never mixes two date modes.
 * Calls to the single-category getters outside a population share the snapshot taken by the first
 * of them. The inbox web model ({@link PatientInfo}) is assembled here from the DAO's
 * {@link InboxCategoryDao.PatientCount} rows.</p>
 */
public class CategoryData {
    private final InboxCategoryDao dao;
    private final InboxDateSearchTypeResolver dateSearchTypes;
    /** The request's search values; a date-mode snapshot completes them into the DAO filter. */
    private final Function<DateSearchType, InboxCategoryDao.Filter> filterFor;
    private final String startDate;
    private final String endDate;
    /** Date mode of the current snapshot, or null until a count or getDateSearchType needs one. */
    private DateSearchType dateSearchType;
    /** Filter of the current snapshot, or null until the next count needs one. */
    private InboxCategoryDao.Filter filter;
    private final boolean patientSearch;
    private final String abnormalStatus;
    private int totalDocs = 0;
    private int totalLabs = 0;
    private int unmatchedLabs = 0;
    private int unmatchedDocs = 0;
    private int totalNumDocs = 0;
    private Integer abnormalCount;
    private boolean populated;
    private boolean failed;
    private int unmatchedHRMCount = 0;
    private int matchedHRMCount = 0;

    private HashMap<Integer, PatientInfo> patients;
    private java.util.function.IntPredicate hrmPatientReadable = patient -> true;

    public int getTotalDocs() {
        requireAvailable();
        return totalDocs;
    }

    public int getTotalLabs() {
        requireAvailable();
        return totalLabs;
    }

    public int getUnmatchedLabs() {
        requireAvailable();
        return unmatchedLabs;
    }

    public int getUnmatchedDocs() {
        requireAvailable();
        return unmatchedDocs;
    }

	public int getUnmatchedHRMCount() {
        requireAvailable();
		return unmatchedHRMCount;
	}

	public int getMatchedHRMCount() {
        requireAvailable();
		return matchedHRMCount;
	}

    public int getTotalNumDocs() {
        requireAvailable();
        return totalNumDocs;
    }

    /**
     * Lazily counts abnormal labs in this request's scope after population.
     * @return int abnormal lab count, or zero before population
     * @throws IllegalStateException if the lazy query fails
     */
    public int getAbnormalCount() {
        requireAvailable();
        if (!populated) return 0;
        if (abnormalCount == null) {
            try {
                abnormalCount = getAbnormalCount(true);
            } catch (SQLException failure) {
                throw new IllegalStateException("Could not read abnormal inbox count", failure);
            }
        }
        return abnormalCount;
    }

    /** @return int normal labs in the most recent population; shares the lazy abnormal query */
    public int getNormalCount() {
        requireAvailable();
        return populated ? totalLabs - getAbnormalCount() : 0;
    }

    public HashMap<Integer, PatientInfo> getPatients() {
        requireAvailable();
        return patients;
    }

    public List<PatientInfo> getPatientList() {
        requireAvailable();
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
        // Callers construct this per request with `new`, so the collaborators come from Spring here.
        this(SpringUtils.getBean(InboxCategoryDao.class), SpringUtils.getBean(InboxDateSearchTypeResolver.class),
                patientLastName, patientFirstName, patientHealthNumber, patientSearch, providerSearch,
                searchProviderNo, status, abnormalStatus, startDate, endDate);
    }

    /**
     * Creates one inbox request's accumulator with explicit collaborators (test seam).
     * @param dao InboxCategoryDao that runs the category counts
     * @param dateSearchTypes InboxDateSearchTypeResolver that reads the date-mode preference
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
    CategoryData(InboxCategoryDao dao, InboxDateSearchTypeResolver dateSearchTypes,
                 String patientLastName, String patientFirstName, String patientHealthNumber,
                 boolean patientSearch, boolean providerSearch, String searchProviderNo,
                 String status, String abnormalStatus, String startDate, String endDate) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.dateSearchTypes = Objects.requireNonNull(dateSearchTypes, "dateSearchTypes");
        this.patientSearch = patientSearch;
        this.abnormalStatus = abnormalStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.filterFor = mode -> new InboxCategoryDao.Filter(patientLastName, patientFirstName, patientHealthNumber,
                patientSearch, providerSearch, searchProviderNo, status, abnormalStatus, startDate, endDate, mode);
        patients = new HashMap<>();
    }

    /** @return DateSearchType the current snapshot's mode, reading the preference if there is none */
    private DateSearchType dateSearchType() {
        if (dateSearchType == null) {
            dateSearchType = dateSearchTypes.resolve();
        }
        return dateSearchType;
    }

    /**
     * Returns the current snapshot's filter. An impossible calendar bound is refused first, so it
     * never costs a preference read or reaches a count query, as before the read left the DAO.
     */
    private InboxCategoryDao.Filter filter() {
        if (filter == null) {
            InboxCategoryDao.parseDateBound(startDate);
            InboxCategoryDao.parseDateBound(endDate);
            filter = filterFor.apply(dateSearchType());
        }
        return filter;
    }

    private int count(Operation operation, String demographicNo) throws SQLException {
        InboxCategoryDao.Counts result;
        try {
            result = dao.count(filter(), operation, demographicNo);
        } catch (org.springframework.dao.DataAccessException | jakarta.persistence.PersistenceException
                 | org.springframework.transaction.TransactionException failure) {
            // Inbox controllers retain their checked-SQL failure path after the DAO migration. This
            // also covers a failed preference read, which happens on the first count of a snapshot.
            throw new SQLException("Could not read inbox category counts", failure);
        }
        int withheld = 0;
        for (InboxCategoryDao.PatientCount row : result.patients()) {
            if (operation == Operation.HRM_PATIENT && !hrmPatientReadable.test(row.id())) {
                // The report list leaves this patient's hospital reports out; neither the patient's
                // name nor the count may appear in the sidebar.
                withheld += row.hrm();
                continue;
            }
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
        return result.total() - withheld;
    }

    /**
     * Sets which patients' hospital (HRM) reports this user may see. Without it every patient is
     * counted, as before the patient-level HRM rules existed.
     *
     * @param readable decides for a demographic number; {@code false} leaves the patient and the
     *                 patient's HRM count out of the matched counts and the patient list
     */
    public void setHrmPatientReadable(java.util.function.IntPredicate readable) {
        this.hrmPatientReadable = java.util.Objects.requireNonNull(readable);
    }

    /**
     * Reports the date mode the counts use: the current snapshot's, or a new snapshot's when no
     * count has run since the last population began.
     * @return String the {@code inboxDateSearchType} value of that mode ({@code serviceObservation}
     *         for a missing, blank or unrecognised preference)
     */
    public String getDateSearchType() { return dateSearchType().preferenceValue(); }
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

    /**
     * Replaces the request's counts and patient summaries and invalidates optional lazy lab aggregates.
     * @throws SQLException if a required population query fails
     */
    public void populateCountsAndPatients() throws SQLException {
        failed = true;
        populated = false;
        abnormalCount = null;
        // Each population reads the date-mode preference once, on its first count.
        dateSearchType = null;
        filter = null;
        totalDocs = totalLabs = unmatchedDocs = unmatchedLabs = totalNumDocs = 0;
        matchedHRMCount = unmatchedHRMCount = 0;
        patients.clear();

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

        // Ordinary inbox loads do not pay for the optional legacy aggregate getters.
        populated = true;
        failed = false;

    }

    private void requireAvailable() {
        if (failed) throw new IllegalStateException("Inbox counts are unavailable");
    }

    public Long getCategoryHash() {
        requireAvailable();
        return Long.valueOf("" + (int) 'A' + totalNumDocs)
                + Long.valueOf("" + (int) 'D' + totalDocs)
                + Long.valueOf("" + (int) 'L' + totalLabs);
    }
}
