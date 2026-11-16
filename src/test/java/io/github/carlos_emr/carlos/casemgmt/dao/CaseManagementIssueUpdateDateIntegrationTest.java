/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A patient issue's {@code update_date} is the time one of its own values changed (finding 361).
 *
 * <p>Both note save paths hand every issue the editor lists to
 * {@link CaseManagementIssueDAO#saveAndUpdateCaseIssues(List)} as detached copies; the REST and
 * issue-edit paths use {@link CaseManagementIssueDAO#saveIssue(CaseManagementIssue)}. Each step
 * here commits its own transaction against the real JPA mapping, so the assertions read what the
 * flush wrote (or did not write), not a persistence context's view.</p>
 */
@DisplayName("CaseManagementIssue update_date moves only with the issue's own values")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CaseManagementIssueUpdateDateIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {

    private static final Date STORED = new Date(1_700_000_000_000L);
    private static final int PATIENT = 987_361;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long catalogueA;
    private Long catalogueB;
    private Long issueA;
    private Long issueB;

    @BeforeEach
    void seedTwoIssuesOfOnePatient() {
        tx().executeWithoutResult(status -> {
            catalogueA = catalogue("A");
            catalogueB = catalogue("B");
            issueA = patientIssue(catalogueA);
            issueB = patientIssue(catalogueB);
        });
    }

    @AfterEach
    void removeOwnedRows() {
        tx().executeWithoutResult(status -> {
            entityManager.createQuery("delete from CaseManagementIssue i where i.demographic_no = :patient")
                    .setParameter("patient", PATIENT).executeUpdate();
            for (Long id : List.of(catalogueA, catalogueB)) {
                Issue owned = entityManager.find(Issue.class, id);
                if (owned != null) entityManager.remove(owned);
            }
        });
    }

    @ParameterizedTest(name = "carried date {0}, issue already loaded {1}")
    @CsvSource({"stored,false", "stored,true", "null,false", "null,true",
            "older,false", "older,true", "now,false", "now,true"})
    void shouldKeepEveryUnchangedIssueDate_whenANoteSaveListsCopiesOfThem(String carried, boolean loaded) {
        CaseManagementIssue copyA = editorCopy(issueA, carried);
        CaseManagementIssue copyB = editorCopy(issueB, carried);

        tx().executeWithoutResult(status -> {
            // The full save loads the previous revision, whose eager issue set puts these rows in
            // the persistence context before the DAO merges the editor's copies onto them.
            if (loaded) assertThat(caseManagementIssueDAO.getIssuesByDemographic(String.valueOf(PATIENT))).hasSize(2);
            caseManagementIssueDAO.saveAndUpdateCaseIssues(List.of(copyA, copyB));
        });

        assertThat(storedDate(issueA)).isEqualTo(STORED.getTime());
        assertThat(storedDate(issueB)).isEqualTo(STORED.getTime());
        assertThat(copyA.getUpdate_date().getTime()).isEqualTo(STORED.getTime());
    }

    @Test
    void shouldStampOnlyTheChangedIssue_whenANoteSaveListsBothIssues() {
        CaseManagementIssue changed = editorCopy(issueA, "stored");
        changed.setResolved(true);
        CaseManagementIssue unchanged = editorCopy(issueB, "stored");
        long start = System.currentTimeMillis();

        tx().executeWithoutResult(status -> caseManagementIssueDAO.saveAndUpdateCaseIssues(List.of(changed, unchanged)));

        assertThat(storedDate(issueA)).isGreaterThanOrEqualTo(start - 1000);
        assertThat(resolved(issueA)).isTrue();
        assertThat(storedDate(issueB)).isEqualTo(STORED.getTime());
        assertThat(resolved(issueB)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "now"})
    void shouldKeepTheDate_whenSaveIssueReceivesAnUnchangedCopy(String carried) {
        // REST input carries no date (null); the issue-change and REST paths set new Date() first.
        CaseManagementIssue copy = editorCopy(issueA, carried);

        tx().executeWithoutResult(status -> caseManagementIssueDAO.saveIssue(copy));

        assertThat(storedDate(issueA)).isEqualTo(STORED.getTime());
    }

    @Test
    void shouldStampTheDate_whenSaveIssueChangesTheCatalogueIssueOfACopy() {
        CaseManagementIssue copy = editorCopy(issueA, "null");
        copy.setIssue_id(catalogueB);
        copy.setIssue(null);
        long start = System.currentTimeMillis();

        tx().executeWithoutResult(status -> caseManagementIssueDAO.saveIssue(copy));

        assertThat(storedDate(issueA)).isGreaterThanOrEqualTo(start - 1000);
    }

    @Test
    void shouldStampTheDate_whenTheCallerChangedTheManagedInstanceItSaves() {
        long start = System.currentTimeMillis();

        tx().executeWithoutResult(status -> {
            // find() returns this same instance to the DAO, so it cannot serve as the "before"
            // state; the dirty check compares with the state Hibernate loaded.
            CaseManagementIssue managed = caseManagementIssueDAO.findById(issueA);
            managed.setAcute(true);
            caseManagementIssueDAO.saveIssue(managed);
        });

        assertThat(storedDate(issueA)).isGreaterThanOrEqualTo(start - 1000);
    }

    @Test
    void shouldKeepTheDate_whenTheManagedInstanceIsSavedUnchanged() {
        tx().executeWithoutResult(status -> caseManagementIssueDAO.saveIssue(caseManagementIssueDAO.findById(issueA)));

        assertThat(storedDate(issueA)).isEqualTo(STORED.getTime());
    }

    @Test
    void shouldStampTheChangeOnce_whenAQueryAutoFlushesItBeforeTheNextSave() {
        CaseManagementIssue copy = editorCopy(issueA, "stored");
        copy.setMajor(true);

        long flushed = tx().execute(status -> {
            caseManagementIssueDAO.saveAndUpdateCaseIssues(List.of(copy));
            // A query on the table auto-flushes the pending change, and the callback stamps it.
            Date stamped = entityManager.createQuery("select i.update_date from CaseManagementIssue i where i.id = :id", Date.class)
                    .setParameter("id", issueA).getSingleResult();
            // The same editor copy listed again in the same transaction is no longer a change.
            caseManagementIssueDAO.saveAndUpdateCaseIssues(List.of(copy));
            return stamped.getTime();
        });

        assertThat(flushed).isNotEqualTo(STORED.getTime());
        assertThat(storedDate(issueA)).isEqualTo(flushed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStampTheCreationTime_whenANewIssueIsSaved(boolean throughList) {
        CaseManagementIssue fresh = new CaseManagementIssue();
        fresh.setDemographic_no(PATIENT);
        fresh.setIssue_id(catalogueA);
        fresh.setType("doctor");
        fresh.setUpdate_date(STORED);
        long start = System.currentTimeMillis();

        tx().executeWithoutResult(status -> {
            if (throughList) caseManagementIssueDAO.saveAndUpdateCaseIssues(List.of(fresh));
            else caseManagementIssueDAO.saveIssue(fresh);
        });
        assertThat(fresh.getId()).isPositive();
        assertThat(storedDate(fresh.getId())).isGreaterThanOrEqualTo(start - 1000);
    }

    /** Builds the detached copy the compact and full save paths pass to the DAO. */
    private CaseManagementIssue editorCopy(Long id, String carried) {
        CaseManagementIssue loaded = tx().execute(status -> caseManagementIssueDAO.findById(id));
        CaseManagementIssue copy = new CaseManagementIssue(loaded);
        copy.setId(loaded.getId());
        copy.setProgram_id(loaded.getProgram_id());
        copy.setNotes(new HashSet<>());
        switch (carried) {
            case "null" -> copy.setUpdate_date(null);
            case "older" -> copy.setUpdate_date(new Date(STORED.getTime() - 86_400_000L));
            case "now" -> copy.setUpdate_date(new Date());
            default -> { }
        }
        return copy;
    }

    private long storedDate(Long id) {
        return tx().execute(status -> entityManager.createQuery(
                        "select i.update_date from CaseManagementIssue i where i.id = :id", Date.class)
                .setParameter("id", id).getSingleResult().getTime());
    }

    private boolean resolved(Long id) {
        return tx().execute(status -> entityManager.createQuery(
                        "select i.resolved from CaseManagementIssue i where i.id = :id", Boolean.class)
                .setParameter("id", id).getSingleResult());
    }

    private Long catalogue(String suffix) {
        Issue issue = new Issue();
        issue.setCode("UPD" + suffix + UUID.randomUUID().toString().substring(0, 8));
        issue.setDescription("Update date issue " + suffix);
        issue.setRole("doctor");
        issue.setType("system");
        entityManager.persist(issue);
        return issue.getId();
    }

    private Long patientIssue(Long catalogue) {
        CaseManagementIssue issue = new CaseManagementIssue();
        issue.setDemographic_no(PATIENT);
        issue.setIssue_id(catalogue);
        issue.setIssue(entityManager.find(Issue.class, catalogue));
        issue.setProgram_id(8);
        issue.setType("doctor");
        issue.setUpdate_date(STORED);
        entityManager.persist(issue);
        return issue.getId();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}
