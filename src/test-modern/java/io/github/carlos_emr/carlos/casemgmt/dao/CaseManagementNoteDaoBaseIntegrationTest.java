/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Base test class for CaseManagementNoteDAO integration tests.
 *
 * <p>Provides shared setup, utilities, and test data creation methods for all
 * CaseManagementNoteDAO test classes. Extends CarlosTestBase to inherit Spring context
 * configuration and SpringUtils anti-pattern handling.</p>
 *
 * @since 2026-02-03
 * @see CaseManagementNoteDAO
 * @see CaseManagementNote
 */
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public abstract class CaseManagementNoteDaoBaseIntegrationTest extends CarlosTestBase {

    @Autowired
    @Qualifier("CaseManagementNoteDAO")
    protected CaseManagementNoteDAO caseManagementNoteDAO;

    @Autowired
    @Qualifier("CaseManagementIssueDAO")
    protected CaseManagementIssueDAO caseManagementIssueDAO;

    @Autowired
    @Qualifier("IssueDAO")
    protected IssueDAO issueDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager;

    /**
     * Creates a CaseManagementNote with the specified demographic and note content.
     *
     * @param demographicNo the demographic number
     * @param noteContent the note content
     * @return the created and persisted note
     */
    protected CaseManagementNote createNote(String demographicNo, String noteContent) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(demographicNo);
        note.setNote(noteContent);
        note.setProviderNo("999998");
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(new Date());
        note.setObservation_date(new Date());
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    /**
     * Creates a CaseManagementNote with the specified demographic, content, and observation date.
     *
     * @param demographicNo the demographic number
     * @param noteContent the note content
     * @param observationDate the observation date
     * @return the created and persisted note
     */
    protected CaseManagementNote createNote(String demographicNo, String noteContent, Date observationDate) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(demographicNo);
        note.setNote(noteContent);
        note.setProviderNo("999998");
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(observationDate);
        note.setObservation_date(observationDate);
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    /**
     * Creates a CaseManagementNote with the specified program and observation date.
     *
     * @param programNo the program number
     * @param observationDate the observation date
     * @return the created and persisted note
     */
    protected CaseManagementNote createNoteWithProgram(Integer programNo, Date observationDate) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no("1001");
        note.setNote("Test note for program " + programNo);
        note.setProviderNo("999998");
        note.setProgram_no(String.valueOf(programNo));
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(observationDate);
        note.setObservation_date(observationDate);
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    /**
     * Creates a CaseManagementNote with the specified signed status.
     *
     * @param demographicNo the demographic number
     * @param signed whether the note is signed
     * @return the created and persisted note
     */
    protected CaseManagementNote createNoteWithSignedStatus(String demographicNo, boolean signed) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(demographicNo);
        note.setNote("Note with signed=" + signed);
        note.setProviderNo("999998");
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(new Date());
        note.setObservation_date(new Date());
        note.setSigned(signed);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    /**
     * Creates a date by adding days to the current date.
     *
     * @param daysToAdd number of days to add (can be negative)
     * @return the calculated date
     */
    protected Date daysFromNow(int daysToAdd) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysToAdd);
        return cal.getTime();
    }

    /**
     * Creates a CaseManagementNote with the specified demographic and provider.
     *
     * @param demographicNo the demographic number
     * @param providerNo the provider number (must match a Provider record)
     * @return the created and persisted note
     */
    protected CaseManagementNote createNoteWithProvider(String demographicNo, String providerNo) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(demographicNo);
        note.setNote("Note by provider " + providerNo);
        note.setProviderNo(providerNo);
        note.setUuid(UUID.randomUUID().toString());
        note.setUpdate_date(new Date());
        note.setObservation_date(new Date());
        note.setSigned(false);
        note.setArchived(false);
        note.setLocked(false);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    /**
     * Creates a specific date.
     *
     * @param year the year
     * @param month the month (1-12)
     * @param day the day of month
     * @return the date
     */
    protected Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Creates and persists an Issue entity.
     *
     * @param code the issue code (e.g., "Concerns")
     * @param description the issue description
     * @return the persisted Issue with generated ID
     */
    protected Issue createIssue(String code, String description) {
        Issue issue = new Issue();
        issue.setCode(code);
        issue.setDescription(description);
        issue.setRole("doctor");
        issue.setType("system");
        issueDAO.saveIssue(issue);
        return issue;
    }

    /**
     * Creates and persists a CaseManagementIssue linked to a demographic and Issue.
     *
     * @param demoNo the demographic number
     * @param issue the Issue entity to link
     * @return the persisted CaseManagementIssue with generated ID
     */
    protected CaseManagementIssue createCaseManagementIssue(String demoNo, Issue issue) {
        CaseManagementIssue cmi = new CaseManagementIssue();
        cmi.setDemographic_no(Integer.valueOf(demoNo));
        cmi.setIssue_id(issue.getId());
        cmi.setAcute(false);
        cmi.setCertain(true);
        cmi.setMajor(false);
        cmi.setResolved(false);
        cmi.setUpdate_date(new Date());
        caseManagementIssueDAO.saveIssue(cmi);
        return cmi;
    }

    /**
     * Creates a note and links it to a CaseManagementIssue via the casemgmt_issue_notes join table.
     *
     * <p>The join table is owned by CaseManagementNote.issues (not inverse), so adding
     * the CMI to the note's issues set and updating the note creates the join entry.</p>
     *
     * @param demoNo the demographic number
     * @param content the note content
     * @param date the observation date
     * @param cmi the CaseManagementIssue to link
     * @return the persisted and linked CaseManagementNote
     */
    protected CaseManagementNote createNoteWithIssue(String demoNo, String content,
                                                     Date date, CaseManagementIssue cmi) {
        CaseManagementNote note = createNote(demoNo, content, date);
        note.getIssues().add(cmi);
        caseManagementNoteDAO.updateNote(note);
        hibernateTemplate.flush();
        return note;
    }
}
