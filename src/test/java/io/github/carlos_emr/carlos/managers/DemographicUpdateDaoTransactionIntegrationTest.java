/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingEdtObecOutputSpecificationRecordDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingObecOutputApplyService;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustArchiveDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicCustArchive;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExtArchive;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.DemographicService;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DemographicConverter;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicExtTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.WebApplicationException;

/**
 * Proves that a demographic update is one unit of work (issue #5298), with the production
 * manager, REST resource and DAOs, real commits and failures raised by the database itself.
 *
 * <p>Three commands are covered. {@link DemographicManagerImpl#updateDemographic} archives the
 * stored patient, saves the new one and archives and saves each extension.
 * {@link DemographicManagerImpl#createUpdateDemographicCust} archives and saves the custom fields.
 * The REST update, {@link DemographicService#updateDemographicData}, runs both after it has saved
 * the waiting-list entry. A failure at any of those writes must leave nothing behind, including
 * the audit rows the manager writes in the same transaction; and an update whose audit row
 * cannot be written is not committed either.</p>
 *
 * <p>The manager and the resource are wrapped in a proxy that reads their own
 * {@code @Transactional} annotations, as production does: remove an annotation and the rollback
 * proofs below fail. Failures are injected with a {@code CHECK} constraint that only a marked
 * test value violates, so the database (H2 by default, the physical MariaDB of the
 * {@code mariadb-native} lanes when {@code CARLOS_TEST_MARIADB_DAO=true}) refuses the write at
 * the patient row, after the archive row was inserted, or at one extension row, after earlier
 * extension rows were written. Committed state is then read on other connections.</p>
 *
 * <p>Two cases use the refusal a client can cause without any injected fault: a stored extension
 * renamed onto a key the patient already holds violates {@code uk_demo_ext_single_value}. That
 * write is an UPDATE of an existing row, which Hibernate defers, so the cases pin where the
 * refusal is raised (by the update command, not by its caller's commit) and, on MariaDB, that the
 * REST update answers it with its documented duplicate-key conflict.</p>
 *
 * @since 2026-11-19
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DemographicUpdateDaoTransactionIntegrationTest.Config.class)
@DisplayName("Demographic update transaction across the demographic DAOs")
@Tag("integration")
@Tag("manager")
@Tag("mariadb-native")
class DemographicUpdateDaoTransactionIntegrationTest {

    /** The REST write under test, as an interface so the proxy reads the resource's own annotations. */
    public interface Writes {
        DemographicTo1 updateDemographicData(DemographicTo1 data);
    }

    /** The production resource with only the signed-in user replaced. */
    private static final class Resource extends DemographicService implements Writes {
        private final LoggedInInfo actor;

        Resource(DemographicDao records, LoggedInInfo actor) {
            super(mock(CdsLegacyProjectionService.class), records);
            this.actor = actor;
        }

        /** Built without a DAO, as legacy callers do: the resource then asks the application for it. */
        Resource(LoggedInInfo actor) {
            super(mock(CdsLegacyProjectionService.class));
            this.actor = actor;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return actor;
        }
    }

    private static final String ACTOR = "999998";
    private static final String SEEDING_USER = "100001";
    private static final String FAULT_PATIENT_NAME = "FAKE-FAULT";
    private static final String FAULT_EXTENSION_KEY = "fault_marker";
    /** Names of the constraints {@link #refuse} adds and {@link #allow} removes. */
    private static final String PATIENT_FAULT = "carlos_update_patient_fault";
    private static final String EXTENSION_FAULT = "carlos_update_ext_fault";
    private static final String CUSTOM_FIELD_FAULT = "carlos_update_cust_fault";
    private static final String AUDIT_FAULT = "carlos_update_audit_fault";
    private static final String FAULT_ALERT = "FAKE-FAULT alert";
    private static final String UPDATE_AUDIT = "DemographicManager.updateDemographic";
    private static final String CUSTOM_FIELD_AUDIT = "DemographicManager.createUpdateDemographicCust";
    private static final String EXTENSION_AUDIT = "DemographicManager.updateExtension";
    /**
     * Production's one-value-per-key index on {@code demographicExt}; a duplicate is MariaDB error 1062.
     * The Flyway lanes have the shipped index, and {@link #prepare} gives a generated schema one of
     * that name.
     */
    private static final String SINGLE_VALUE_KEY = "uk_demo_ext_single_value";
    /** A second extension key: the one {@link #storeHeldExtension} gives the patient. */
    private static final String HELD_KEY = "hPhoneExt";

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DemographicDao patients;
    @Autowired private DemographicArchiveDao patientArchives;
    @Autowired private DemographicExtDao extensions;
    @Autowired private DemographicExtArchiveDao extensionArchives;
    @Autowired private DemographicCustDao customFields;
    @Autowired private DemographicCustArchiveDao customFieldArchives;
    @Autowired private WaitingListDao waitingLists;
    @Autowired private OscarLogDao auditLogs;
    @PersistenceContext private EntityManager em;

    private JdbcTemplate jdbc;
    private SecurityInfoManager privileges;
    private LoggedInInfo actor;
    private DemographicManager manager;
    private Writes api;
    private Object previousAuditDao;
    private int patientId;
    private int mergedChildId;
    private int mergeLinkId;
    private int extensionId;
    private int waitingListId;

    @BeforeEach
    void prepare() {
        jdbc = new JdbcTemplate(dataSource);
        if (!flywaySchema()) {
            // Demographic's read-only formulas refer to these legacy lookup tables.
            // The Flyway lanes use the shipped definitions without alteration.
            jdbc.execute("CREATE TABLE IF NOT EXISTS admission(client_id INT, admission_status VARCHAR(20), program_id INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS program(id INT, type VARCHAR(20))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS health_safety(demographic_no INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS lst_gender(code VARCHAR(10), description VARCHAR(100))");
            // One row per patient and key, as the shipped index enforces for every key these tests use.
            // Hibernate does not generate it, and a duplicate is how MariaDB refuses a stored extension
            // renamed onto a key the patient already holds.
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + SINGLE_VALUE_KEY
                    + " ON demographicExt (demographic_no, key_val)");
        }
        previousAuditDao = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", auditLogs);

        actor = signedIn(ACTOR);
        privileges = mock(SecurityInfoManager.class);
        seedPatient();
        allowUpdateOf(patientId);

        DemographicManagerImpl managerTarget = new DemographicManagerImpl();
        ReflectionTestUtils.setField(managerTarget, "demographicDao", patients);
        ReflectionTestUtils.setField(managerTarget, "demographicArchiveDao", patientArchives);
        ReflectionTestUtils.setField(managerTarget, "demographicExtDao", extensions);
        ReflectionTestUtils.setField(managerTarget, "demographicExtArchiveDao", extensionArchives);
        ReflectionTestUtils.setField(managerTarget, "demographicCustDao", customFields);
        ReflectionTestUtils.setField(managerTarget, "demographicCustArchiveDao", customFieldArchives);
        ReflectionTestUtils.setField(managerTarget, "securityInfoManager", privileges);
        manager = transactional(DemographicManager.class, managerTarget);

        api = restApi(new Resource(patients, actor));
    }

    @AfterEach
    void removeFaultsAndRestoreAudit() {
        try {
            allow("demographic", PATIENT_FAULT);
            allow("demographicExt", EXTENSION_FAULT);
            allow("demographiccust", CUSTOM_FIELD_FAULT);
            allow("log", AUDIT_FAULT);
        } finally {
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousAuditDao);
        }
    }

    // ------------------------------------------------------------------ manager command

    @Test
    @DisplayName("should commit the archive, the patient and every extension when all writes succeed")
    void shouldCommitArchivePatientAndExtensions_whenEveryWriteSucceeds() {
        long auditsBefore = audits(UPDATE_AUDIT);
        Demographic changed = storedPatient();
        changed.setFirstName("Changed");
        changed.setExtras(new DemographicExt[] {
            existingExtension("555-0199"), newExtension("hPhoneExt", "42") });

        manager.updateDemographic(actor, changed);

        assertThat(patientFirstName()).isEqualTo("Changed");
        assertThat(patientUpdatedBy()).as("the update is attributed to the signed-in user").isEqualTo(ACTOR);
        assertThat(archivedPatientRows()).isEqualTo(1);
        assertThat(archivedFirstName()).as("the archive holds the row as it was").isEqualTo("Original");
        assertThat(archivedUpdatedBy()).isEqualTo(SEEDING_USER);
        assertThat(extensionValues()).containsExactlyInAnyOrder("demo_cell=555-0199", "hPhoneExt=42");
        assertThat(archivedExtensionValues()).containsExactly("demo_cell=555-0100");
        assertThat(audits(UPDATE_AUDIT)).isEqualTo(auditsBefore + 1);
        assertThat(mergeLinkIsIntact()).as("the merged child stays linked, by its original row").isTrue();
    }

    @Test
    @DisplayName("should leave no archive row behind when the patient write fails after the archive was created")
    void shouldLeaveNoArchiveRow_whenThePatientWriteFailsAfterTheArchive() {
        long auditsBefore = audits(UPDATE_AUDIT);
        refuse("demographic", PATIENT_FAULT, "first_name <> '" + FAULT_PATIENT_NAME + "'");
        Demographic changed = storedPatient();
        changed.setFirstName(FAULT_PATIENT_NAME);
        changed.setExtras(new DemographicExt[] { existingExtension("555-0199") });

        assertThatThrownBy(() -> manager.updateDemographic(actor, changed))
                .satisfies(failure -> assertThat(rootCauseMessage(failure)).containsIgnoringCase(PATIENT_FAULT));

        assertNothingOfTheUpdateRemains(auditsBefore);
    }

    @Test
    @DisplayName("should leave no part of the update behind when a later extension write fails")
    void shouldLeaveNoPartialUpdate_whenALaterExtensionWriteFails() {
        long auditsBefore = audits(UPDATE_AUDIT);
        refuse("demographicExt", EXTENSION_FAULT, "key_val <> '" + FAULT_EXTENSION_KEY + "'");
        Demographic changed = storedPatient();
        changed.setFirstName("Changed");
        changed.setExtras(new DemographicExt[] {
            existingExtension("555-0199"), newExtension("hPhoneExt", "42"), newExtension(FAULT_EXTENSION_KEY, "x") });

        assertThatThrownBy(() -> manager.updateDemographic(actor, changed))
                .satisfies(failure -> assertThat(rootCauseMessage(failure)).containsIgnoringCase(EXTENSION_FAULT));

        assertNothingOfTheUpdateRemains(auditsBefore);
    }

    @Test
    @DisplayName("should raise a refused change to a stored extension itself, before the caller's transaction commits")
    void shouldRaiseTheRefusalItself_whenAStoredExtensionTakesAHeldKey() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long extensionAuditsBefore = audits(EXTENSION_AUDIT);
        storeHeldExtension();
        Demographic changed = storedPatient();
        changed.setFirstName("Changed");
        DemographicExt renamed = existingExtension("555-0199");
        renamed.setKey(HELD_KEY);
        changed.setExtras(new DemographicExt[] { renamed });
        AtomicReference<RuntimeException> raisedByTheCommand = new AtomicReference<>();

        // A stored row is changed by an UPDATE that Hibernate leaves for the next flush. Left for the
        // commit of the transaction the command joined (here the caller's, as in the REST update), the
        // database's refusal would arrive after the caller's own code had finished.
        new TransactionTemplate(transactions).executeWithoutResult(caller -> {
            try {
                manager.updateDemographic(actor, changed);
            } catch (RuntimeException refusal) {
                raisedByTheCommand.set(refusal);
                caller.setRollbackOnly();
            }
        });

        assertThat(raisedByTheCommand.get()).as("the refusal, raised by the update command").isNotNull()
                .satisfies(refusal -> assertThat(rootCauseMessage(refusal)).containsIgnoringCase(SINGLE_VALUE_KEY));
        assertNothingOfTheUpdateRemains(auditsBefore, "demo_cell=555-0100", HELD_KEY + "=42");
        assertThat(audits(EXTENSION_AUDIT)).as("extension audit rows").isEqualTo(extensionAuditsBefore);
    }

    @Test
    @DisplayName("should write nothing when the signed-in user may not update demographics")
    void shouldWriteNothing_whenTheActorMayNotUpdateDemographics() {
        long auditsBefore = audits(UPDATE_AUDIT);
        when(privileges.hasPrivilege(actor, "_demographic", "u", (String) null)).thenReturn(false);
        Demographic changed = storedPatient();
        changed.setFirstName("Changed");
        changed.setExtras(new DemographicExt[] { existingExtension("555-0199") });

        assertThatThrownBy(() -> manager.updateDemographic(actor, changed)).isInstanceOf(SecurityException.class);

        assertNothingOfTheUpdateRemains(auditsBefore);
    }

    @Test
    @DisplayName("should not commit an update whose audit row cannot be written")
    void shouldNotCommitTheUpdate_whenItsAuditRowCannotBeWritten() {
        long auditsBefore = audits(UPDATE_AUDIT);
        // Only this patient's update audit row is refused: other cases have committed theirs, and
        // the database checks the rows it already holds when the constraint is added.
        refuse("log", AUDIT_FAULT, "action <> '" + UPDATE_AUDIT + "' OR data <> 'demographicNo=" + patientId + "'");
        Demographic changed = storedPatient();
        changed.setFirstName("Changed");
        changed.setExtras(new DemographicExt[] { existingExtension("555-0199") });

        // The audit helper logs its own failure and returns; the refused row has already marked the
        // shared transaction, which then refuses to commit the patient without it.
        assertThatThrownBy(() -> manager.updateDemographic(actor, changed))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertNothingOfTheUpdateRemains(auditsBefore);
    }

    // ------------------------------------------------------------------ custom-field command

    @Test
    @DisplayName("should leave no custom-field archive behind when the custom-field write fails")
    void shouldLeaveNoCustomFieldArchive_whenTheCustomFieldWriteFails() {
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        refuse("demographiccust", CUSTOM_FIELD_FAULT, "cust3 <> '" + FAULT_ALERT + "'");
        DemographicCust changed = new DemographicCust();
        changed.setId(patientId);
        changed.setAlert(FAULT_ALERT);
        changed.setNotes("Changed notes");

        assertThatThrownBy(() -> manager.createUpdateDemographicCust(actor, changed))
                .satisfies(failure -> assertThat(rootCauseMessage(failure)).containsIgnoringCase(CUSTOM_FIELD_FAULT));

        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should refuse custom fields changed on the stored row itself, whose previous values are gone")
    void shouldRefuseTheStoredRow_whenCustomFieldsWereChangedInPlace() {
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        TransactionTemplate callerTransaction = new TransactionTemplate(transactions);

        // The message pins the refusal to the manager's own check, not to any IllegalArgumentException.
        assertThatThrownBy(() -> callerTransaction.executeWithoutResult(status -> {
            // Inside a transaction this is the managed row, the same object the manager finds.
            DemographicCust stored = manager.getDemographicCust(actor, patientId);
            stored.setAlert("Changed alert");
            manager.createUpdateDemographicCust(actor, stored);
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Custom fields must be saved from a copy, not from the stored row.");

        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    // ------------------------------------------------------------------ REST command

    @Test
    @DisplayName("should commit the custom fields, the waiting-list entry and the patient as one REST update")
    void shouldCommitEveryCompanionWrite_whenTheRestUpdateSucceeds() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));
        data.getExtras().add(extensionPayload(null, "hPhoneExt", "42"));

        put(data);

        assertThat(patientFirstName()).isEqualTo("Changed");
        assertThat(patientUpdatedBy()).as("the update is attributed to the signed-in user").isEqualTo(ACTOR);
        assertThat(archivedPatientRows()).isEqualTo(1);
        assertThat(archivedFirstName()).as("the archive holds the row as it was").isEqualTo("Original");
        assertThat(extensionValues()).containsExactlyInAnyOrder("demo_cell=555-0199", "hPhoneExt=42");
        assertThat(archivedExtensionValues()).containsExactly("demo_cell=555-0100");
        assertThat(customFieldAlert()).isEqualTo("Changed alert");
        assertThat(customFieldNotes()).isEqualTo("Changed notes");
        assertThat(archivedCustomFieldAlerts()).as("the previous custom fields are archived, not the new ones")
                .containsExactly("Original alert");
        assertThat(waitingListNotes("N")).containsExactly("Changed note");
        assertThat(waitingListNotes("Y")).containsExactly("Original note");
        assertThat(audits(UPDATE_AUDIT)).isEqualTo(auditsBefore + 1);
        assertThat(audits(CUSTOM_FIELD_AUDIT)).isEqualTo(customFieldAuditsBefore + 1);
        assertThat(mergeLinkIsIntact()).as("the merged child stays linked, by its original row").isTrue();
    }

    @Test
    @DisplayName("should roll back the custom fields and the waiting-list entry when the patient write fails")
    void shouldRollBackCompanionWrites_whenThePatientWriteFails() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        refuse("demographic", PATIENT_FAULT, "first_name <> '" + FAULT_PATIENT_NAME + "'");
        DemographicTo1 data = restPayload(FAULT_PATIENT_NAME);
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));

        assertThatThrownBy(() -> put(data))
                .satisfies(failure -> assertThat(rootCauseMessage(failure)).containsIgnoringCase(PATIENT_FAULT));

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should roll back every companion write when a later extension write fails")
    void shouldRollBackCompanionWrites_whenALaterExtensionWriteFails() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        refuse("demographicExt", EXTENSION_FAULT, "key_val <> '" + FAULT_EXTENSION_KEY + "'");
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));
        data.getExtras().add(extensionPayload(null, "hPhoneExt", "42"));
        data.getExtras().add(extensionPayload(null, FAULT_EXTENSION_KEY, "x"));

        assertThatThrownBy(() -> put(data))
                .satisfies(failure -> assertThat(rootCauseMessage(failure)).containsIgnoringCase(EXTENSION_FAULT));

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should answer the duplicate-key conflict and keep nothing when a stored extension takes a key the patient holds")
    void shouldAnswerTheDuplicateKeyConflict_whenAStoredExtensionTakesAHeldKey() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        long extensionAuditsBefore = audits(EXTENSION_AUDIT);
        storeHeldExtension();
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, HELD_KEY, "555-0199"));

        Throwable refusal = catchThrowable(() -> put(data));

        if (physicalMariaDb()) {
            // The contract clients are given: the same answer as for two new extensions with one key.
            assertThat(refusal).isInstanceOfSatisfying(WebApplicationException.class, conflict -> {
                assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
                assertThat(conflict.getResponse().getEntity()).isInstanceOfSatisfying(ErrorResponse.class,
                        error -> assertThat(error.getCode()).isEqualTo("DUPLICATE_DEMOGRAPHIC_EXTENSION_KEY"));
            });
        } else {
            // H2 reports a duplicate under its own error code, which the resource does not take for
            // MariaDB's 1062, so here the refusal is only shown to be the database's.
            assertThat(refusal).isNotNull();
            assertThat(rootCauseMessage(refusal)).containsIgnoringCase(SINGLE_VALUE_KEY);
        }
        assertNothingOfTheUpdateRemains(auditsBefore, "demo_cell=555-0100", HELD_KEY + "=42");
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
        assertThat(audits(EXTENSION_AUDIT)).as("extension audit rows").isEqualTo(extensionAuditsBefore);
    }

    @Test
    @DisplayName("should store both extensions when a stored one gives up the key a new one takes")
    void shouldStoreBothExtensions_whenAStoredExtensionFreesTheKeyANewOneTakes() {
        long auditsBefore = audits(UPDATE_AUDIT);
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, HELD_KEY, "555-0100"));
        data.getExtras().add(extensionPayload(null, "demo_cell", "555-0111"));

        // Valid in the order given. Were the stored row's UPDATE left until after the INSERT, the new
        // row would meet the key the stored one still held and the request would be refused.
        put(data);

        assertThat(patientFirstName()).isEqualTo("Changed");
        assertThat(extensionValues()).containsExactlyInAnyOrder(HELD_KEY + "=555-0100", "demo_cell=555-0111");
        assertThat(archivedExtensionValues()).as("the stored extension as it was").containsExactly("demo_cell=555-0100");
        assertThat(audits(UPDATE_AUDIT)).isEqualTo(auditsBefore + 1);
    }

    @Test
    @DisplayName("should refuse the REST update and keep nothing when the waiting-list entry it names is gone")
    void shouldRefuseAndKeepNothing_whenTheWaitingListEntryIsGone() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        DemographicTo1 data = restPayload("Changed");
        // The entry is removed after the client loaded the form that names it.
        jdbc.update("DELETE FROM waitingList WHERE listID=? AND demographic_no=?", waitingListId, patientId);

        // Saving the patient and answering success would lose the note the client sent, silently.
        assertThatThrownBy(() -> put(data)).isInstanceOfSatisfying(WebApplicationException.class,
                refusal -> assertConflict(refusal, "WAITING_LIST_ENTRY_NOT_FOUND"));

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertThat(customFieldAlert()).as("demographiccust alert").isEqualTo("Original alert");
        assertThat(archivedCustomFieldAlerts()).as("demographiccustArchive rows").isEmpty();
        assertThat(audits(CUSTOM_FIELD_AUDIT)).as("custom-field audit rows").isEqualTo(customFieldAuditsBefore);
        assertThat(waitingListNotes("N")).as("current waiting-list entries").isEmpty();
        assertThat(waitingListNotes("Y")).as("historical waiting-list entries").isEmpty();
    }

    @Test
    @DisplayName("should save the patient and leave the waiting list alone when the request names no list")
    void shouldSaveThePatient_whenTheRequestNamesNoWaitingList() {
        DemographicTo1 data = restPayload("Changed");
        // The form's value for "no list": not a list the patient could be missing from.
        data.setWaitingListID(0);

        put(data);

        assertThat(patientFirstName()).isEqualTo("Changed");
        assertThat(waitingListNotes("N")).as("current waiting-list entries").containsExactly("Original note");
        assertThat(waitingListNotes("Y")).as("historical waiting-list entries").isEmpty();
    }

    @Test
    @DisplayName("should refuse the REST update before either chart changes when a stored extension is another patient's")
    void shouldRefuseBeforeEitherChartChanges_whenAStoredExtensionIsAnotherPatients() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        int foreignExtension = storeExtension(mergedChildId, "demo_cell", "555-0177");
        DemographicTo1 data = restPayload("Changed");
        // Sent as this patient's: the id alone would re-point the other patient's row and overwrite it.
        data.getExtras().add(extensionPayload(foreignExtension, HELD_KEY, "555-0188"));

        assertThatThrownBy(() -> put(data)).isInstanceOfSatisfying(WebApplicationException.class,
                refusal -> assertConflict(refusal, "DEMOGRAPHIC_EXTENSION_NOT_FOUND"));

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
        assertThat(extensionValues(mergedChildId)).as("the other patient's extensions").containsExactly("demo_cell=555-0177");
        assertThat(archivedExtensionRows(mergedChildId)).as("the other patient's extension archive").isZero();
    }

    @Test
    @DisplayName("should keep every extension with the patient being updated, whatever owner the request names")
    void shouldKeepEveryExtensionWithThePatient_whateverOwnerWasSent() {
        DemographicTo1 data = restPayload("Changed");
        DemographicExtTo1 stored = extensionPayload(extensionId, "demo_cell", "555-0199");
        stored.setDemographicNo(mergedChildId);
        DemographicExtTo1 added = extensionPayload(null, HELD_KEY, "42");
        added.setDemographicNo(mergedChildId);
        data.getExtras().add(stored);
        data.getExtras().add(added);

        put(data);

        // The caller may update this patient; the owner it names must not move or create rows elsewhere.
        assertThat(extensionValues()).containsExactlyInAnyOrder("demo_cell=555-0199", HELD_KEY + "=42");
        assertThat(extensionValues(mergedChildId)).as("the other patient's extensions").isEmpty();
    }

    @Test
    @DisplayName("should archive the previous version code when an OBEC response invalidates it")
    void shouldArchiveThePreviousVersionCode_whenAnObecResponseInvalidatesIt() {
        jdbc.update("UPDATE demographic SET hin=?, ver=? WHERE demographic_no=?", "9999999999", "AB", patientId);
        when(privileges.getReadableDemographicIds(eq(actor), any())).thenReturn(Set.of(patientId));
        ProxyFactory factory = new ProxyFactory(
                new BillingObecOutputApplyService(mock(BatchEligibilityDao.class), customFields, manager));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        BillingObecOutputApplyService obec = (BillingObecOutputApplyService) factory.getProxy();

        // The service runs in one transaction, so the patient it reads is the stored row itself.
        BillingObecOutputApplyService.ApplyResult result = obec.applyOutputSpec(actor,
                List.of(new BillingEdtObecOutputSpecificationRecordDto("9999999999", "AB", "65")));

        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ver FROM demographic WHERE demographic_no=?", String.class, patientId))
                .as("the stored version code").isEqualTo("##");
        assertThat(jdbc.queryForList("SELECT ver FROM demographicArchive WHERE demographic_no=?", String.class, patientId))
                .as("the archive holds the row as it was").containsExactly("AB");
    }

    @Test
    @DisplayName("should refuse the REST update before any write when the user may not update this patient")
    void shouldWriteNothing_whenTheActorMayNotUpdateThisPatient() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        when(privileges.hasPrivilege(actor, "_demographic", "u", patientId)).thenReturn(false);
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));

        assertThatThrownBy(() -> put(data)).isInstanceOfSatisfying(WebApplicationException.class,
                refusal -> assertThat(refusal.getResponse().getStatus()).isEqualTo(403));

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should keep requiring read access before the REST update saves custom fields")
    void shouldWriteNothing_whenTheActorMayNotReadDemographics() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        when(privileges.hasPrivilege(actor, "_demographic", "r", (String) null)).thenReturn(false);
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));

        assertThatThrownBy(() -> put(data)).isInstanceOf(SecurityException.class);

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should answer 404 and write nothing when the patient does not exist")
    void shouldAnswerNotFound_whenThePatientDoesNotExist() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        int missing = mergedChildId + 1_000_000;
        when(privileges.hasPrivilege(actor, "_demographic", "u", missing)).thenReturn(true);
        DemographicTo1 data = restPayload("Changed");
        data.setDemographicNo(missing);

        assertThatThrownBy(() -> put(data)).isInstanceOfSatisfying(WebApplicationException.class,
                refusal -> assertThat(refusal.getResponse().getStatus()).isEqualTo(404));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demographiccust WHERE demographic_no=?", Long.class, missing))
                .as("custom fields of the missing patient").isZero();
        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should pass on a failure to lock the patient, before any write")
    void shouldPropagateALockFailure_beforeAnyWrite() {
        long auditsBefore = audits(UPDATE_AUDIT);
        long customFieldAuditsBefore = audits(CUSTOM_FIELD_AUDIT);
        IllegalStateException storageFailure = new IllegalStateException("Synthetic storage failure");
        DemographicDao failingRecords = mock(DemographicDao.class);
        when(failingRecords.findFreshForUpdate(patientId)).thenThrow(storageFailure);
        Writes failing = restApi(new Resource(failingRecords, actor));

        // Not a concurrency conflict, so it is not turned into a 409: the caller sees the failure itself.
        assertThatThrownBy(() -> put(failing, restPayload("Changed"))).isSameAs(storageFailure);

        assertNothingOfTheUpdateRemains(auditsBefore);
        assertNoCompanionWriteRemains(customFieldAuditsBefore);
    }

    @Test
    @DisplayName("should lock through the application's DAO when the resource was built without one")
    void shouldCommitTheUpdate_whenTheResourceWasBuiltWithoutADao() {
        Writes legacy = restApi(new Resource(actor));
        DemographicTo1 data = restPayload("Changed");
        data.getExtras().add(extensionPayload(extensionId, "demo_cell", "555-0199"));

        put(legacy, data);

        assertThat(patientFirstName()).isEqualTo("Changed");
        assertThat(archivedPatientRows()).isEqualTo(1);
        assertThat(archivedCustomFieldAlerts()).containsExactly("Original alert");
        assertThat(extensionValues()).containsExactly("demo_cell=555-0199");
    }

    // ------------------------------------------------------------------ fixtures

    private void seedPatient() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Demographic patient = samplePatient("Original");
            Demographic child = samplePatient("Merged");
            em.persist(patient);
            em.persist(child);
            patientId = patient.getDemographicNo();
            mergedChildId = child.getDemographicNo();

            DemographicMerged link = new DemographicMerged();
            link.setDemographicNo(mergedChildId);
            link.setMergedTo(patientId);
            link.setLastUpdateUser(SEEDING_USER);
            link.setLastUpdateDate(new Date());
            em.persist(link);
            mergeLinkId = link.getId();

            DemographicExt cell = new DemographicExt();
            cell.setDemographicNo(patientId);
            cell.setProviderNo(SEEDING_USER);
            cell.setKey("demo_cell");
            cell.setValue("555-0100");
            em.persist(cell);
            extensionId = cell.getId();

            DemographicCust custom = new DemographicCust();
            custom.setId(patientId);
            custom.setAlert("Original alert");
            custom.setNotes("Original notes");
            em.persist(custom);

            // One list per patient, so renumbering in one test cannot touch another test's entry.
            waitingListId = patientId;
            WaitingList entry = new WaitingList();
            entry.setListId(waitingListId);
            entry.setDemographicNo(patientId);
            entry.setNote("Original note");
            entry.setPosition(1);
            entry.setOnListSince(new Date());
            entry.setHistory(false);
            em.persist(entry);
        });
    }

    private static Demographic samplePatient(String firstName) {
        Demographic patient = new Demographic();
        patient.setFirstName(firstName);
        patient.setLastName("FAKE-UPDATE");
        patient.setPrefName("");
        patient.setSex("M");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("06");
        patient.setDateOfBirth("15");
        patient.setPatientStatus("AC");
        patient.setLastUpdateUser(SEEDING_USER);
        return patient;
    }

    private void allowUpdateOf(int demographicNo) {
        for (String right : List.of("r", "u", "w")) {
            when(privileges.hasPrivilege(actor, "_demographic", right, (String) null)).thenReturn(true);
        }
        when(privileges.hasPrivilege(actor, "_demographic", "u", demographicNo)).thenReturn(true);
    }

    private static LoggedInInfo signedIn(String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        Security security = new Security();
        security.setProviderNo(providerNo);
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLoggedInSecurity(security);
        return loggedInInfo;
    }

    /** Wraps a target in the advice production applies, driven by the target's own annotations. */
    private <T> T transactional(Class<T> contract, Object target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(contract);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return contract.cast(factory.getProxy());
    }

    /** The REST resource with this test's manager and privileges, behind its own transaction advice. */
    private Writes restApi(Resource resource) {
        ReflectionTestUtils.setField(resource, "securityInfoManager", privileges);
        ReflectionTestUtils.setField(resource, "demographicManager", manager);
        return transactional(Writes.class, resource);
    }

    /** The patient as a caller outside any transaction holds it: a detached copy of the stored row. */
    private Demographic storedPatient() {
        return patients.getDemographicById(patientId);
    }

    private DemographicExt existingExtension(String newValue) {
        DemographicExt extension = newExtension("demo_cell", newValue);
        extension.setId(extensionId);
        return extension;
    }

    private DemographicExt newExtension(String key, String value) {
        DemographicExt extension = new DemographicExt();
        extension.setDemographicNo(patientId);
        extension.setProviderNo(ACTOR);
        extension.setKey(key);
        extension.setValue(value);
        return extension;
    }

    /** Stores an extension for {@code demographicNo} and returns its id. */
    private int storeExtension(int demographicNo, String key, String value) {
        return new TransactionTemplate(transactions).execute(status -> {
            DemographicExt extension = newExtension(key, value);
            extension.setDemographicNo(demographicNo);
            extension.setProviderNo(SEEDING_USER);
            em.persist(extension);
            return extension.getId();
        });
    }

    /** The resource's refusal of a request that no longer matches what is stored: HTTP 409 with a code. */
    private static void assertConflict(WebApplicationException refusal, String code) {
        assertThat(refusal.getResponse().getStatus()).isEqualTo(409);
        assertThat(refusal.getResponse().getEntity()).isInstanceOfSatisfying(ErrorResponse.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    /** Gives the patient a second stored extension, under {@link #HELD_KEY}. */
    private void storeHeldExtension() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            DemographicExt held = newExtension(HELD_KEY, "42");
            held.setProviderNo(SEEDING_USER);
            em.persist(held);
        });
    }

    private static boolean flywaySchema() {
        return "flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"));
    }

    private static boolean physicalMariaDb() {
        return "true".equals(System.getenv("CARLOS_TEST_MARIADB_DAO"));
    }

    /** A complete REST payload for the stored patient that also changes the companion records. */
    private DemographicTo1 restPayload(String firstName) {
        DemographicTo1 data = new DemographicConverter().getAsTransferObject(actor, storedPatient());
        data.setFirstName(firstName);
        data.setExtras(new ArrayList<>());
        data.setAlert("Changed alert");
        data.setNotes("Changed notes");
        data.setWaitingListID(waitingListId);
        data.setWaitingListNote("Changed note");
        return data;
    }

    private DemographicExtTo1 extensionPayload(Integer id, String key, String value) {
        DemographicExtTo1 extension = new DemographicExtTo1();
        extension.setId(id);
        extension.setDemographicNo(patientId);
        extension.setKey(key);
        extension.setValue(value);
        return extension;
    }

    /** Sends the REST update with the application's static bean lookups bound to this test's database. */
    private DemographicTo1 put(DemographicTo1 data) {
        return put(api, data);
    }

    private DemographicTo1 put(Writes resource, DemographicTo1 data) {
        try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class)) {
            beans.when(() -> SpringUtils.getBean(WaitingListDao.class)).thenReturn(waitingLists);
            beans.when(() -> SpringUtils.getBean(DemographicDao.class)).thenReturn(patients);
            return resource.updateDemographicData(data);
        }
    }

    /** Makes the database refuse any row of {@code table} that violates {@code condition}. */
    private void refuse(String table, String constraint, String condition) {
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint + " CHECK (" + condition + ")");
    }

    /** Removes a constraint {@link #refuse} added; harmless when the test added none. */
    private void allow(String table, String constraint) {
        jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
    }

    // ------------------------------------------------------------------ committed state

    private void assertNothingOfTheUpdateRemains(long auditsBefore) {
        assertNothingOfTheUpdateRemains(auditsBefore, "demo_cell=555-0100");
    }

    /** As above, for a patient whose stored extensions are {@code storedExtensions}. */
    private void assertNothingOfTheUpdateRemains(long auditsBefore, String... storedExtensions) {
        assertThat(patientFirstName()).as("patient row").isEqualTo("Original");
        assertThat(patientUpdatedBy()).as("patient row attribution").isEqualTo(SEEDING_USER);
        assertThat(archivedPatientRows()).as("demographicArchive rows").isZero();
        assertThat(extensionValues()).as("demographicExt rows").containsExactlyInAnyOrder(storedExtensions);
        assertThat(archivedExtensionValues()).as("demographicExtArchive rows").isEmpty();
        assertThat(audits(UPDATE_AUDIT)).as("update audit rows").isEqualTo(auditsBefore);
        assertThat(mergeLinkIsIntact()).as("merge link").isTrue();
    }

    private void assertNoCompanionWriteRemains(long customFieldAuditsBefore) {
        assertThat(customFieldAlert()).as("demographiccust alert").isEqualTo("Original alert");
        assertThat(customFieldNotes()).as("demographiccust notes").isEqualTo("Original notes");
        assertThat(archivedCustomFieldAlerts()).as("demographiccustArchive rows").isEmpty();
        assertThat(waitingListNotes("N")).as("current waiting-list entries").containsExactly("Original note");
        assertThat(waitingListNotes("Y")).as("historical waiting-list entries").isEmpty();
        assertThat(audits(CUSTOM_FIELD_AUDIT)).as("custom-field audit rows").isEqualTo(customFieldAuditsBefore);
    }

    private String patientFirstName() {
        return jdbc.queryForObject("SELECT first_name FROM demographic WHERE demographic_no=?", String.class, patientId);
    }

    private String patientUpdatedBy() {
        return jdbc.queryForObject("SELECT lastUpdateUser FROM demographic WHERE demographic_no=?", String.class, patientId);
    }

    private long archivedPatientRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=?", Long.class, patientId);
    }

    private String archivedFirstName() {
        return jdbc.queryForObject("SELECT first_name FROM demographicArchive WHERE demographic_no=?",
                String.class, patientId);
    }

    private String archivedUpdatedBy() {
        return jdbc.queryForObject("SELECT lastUpdateUser FROM demographicArchive WHERE demographic_no=?",
                String.class, patientId);
    }

    private String customFieldAlert() {
        return jdbc.queryForObject("SELECT cust3 FROM demographiccust WHERE demographic_no=?", String.class, patientId);
    }

    private String customFieldNotes() {
        return jdbc.queryForObject("SELECT content FROM demographiccust WHERE demographic_no=?", String.class, patientId);
    }

    private List<String> archivedCustomFieldAlerts() {
        return jdbc.queryForList("SELECT cust3 FROM demographiccustArchive WHERE demographic_no=? ORDER BY id",
                String.class, patientId);
    }

    private List<String> waitingListNotes(String history) {
        return jdbc.queryForList("SELECT note FROM waitingList WHERE listID=? AND demographic_no=? AND is_history=? ORDER BY id",
                String.class, waitingListId, patientId, history);
    }

    /** Extension rows as {@code key=value}, read through the DAO because {@code value} is a reserved word in H2. */
    private List<String> extensionValues() {
        return extensionValues(patientId);
    }

    private List<String> extensionValues(int demographicNo) {
        return extensions.getDemographicExtByDemographicNo(demographicNo).stream()
                .map(extension -> extension.getKey() + "=" + extension.getValue()).toList();
    }

    private long archivedExtensionRows(int demographicNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=?",
                Long.class, demographicNo);
    }

    private List<String> archivedExtensionValues() {
        return extensionArchives.getDemographicExtArchiveByDemoAndKey(patientId, "demo_cell").stream()
                .map(archive -> archive.getKey() + "=" + archive.getValue()).toList();
    }

    private long audits(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action=? AND provider_no=?", Long.class, action, ACTOR);
    }

    /** True while the one seeded merge row is still the only link to the patient, unchanged. */
    private boolean mergeLinkIsIntact() {
        long links = jdbc.queryForObject("SELECT COUNT(*) FROM demographic_merged WHERE merged_to=?", Long.class, patientId);
        long original = jdbc.queryForObject("SELECT COUNT(*) FROM demographic_merged WHERE id=? AND merged_to=? "
                + "AND demographic_no=? AND deleted=0 AND lastUpdateUser=?", Long.class,
                mergeLinkId, patientId, mergedChildId, SEEDING_USER);
        return links == 1 && original == 1;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("patient_update");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase ownedDatabase) {
            return ownedDatabase.dataSource();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                IsolatedJpaTestDatabase ownedDatabase) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    Demographic.class.getName(), DemographicArchive.class.getName(), DemographicMerged.class.getName(),
                    DemographicExt.class.getName(), DemographicExtArchive.class.getName(),
                    DemographicCust.class.getName(), DemographicCustArchive.class.getName(),
                    WaitingList.class.getName(), Provider.class.getName(), OscarLog.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(ownedDatabase.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean DemographicDao demographicDao() { return new DemographicDaoImpl(); }
        @Bean DemographicArchiveDao demographicArchiveDao() { return new DemographicArchiveDaoImpl(); }
        @Bean DemographicExtDao demographicExtDao() { return new DemographicExtDaoImpl(); }
        @Bean DemographicExtArchiveDao demographicExtArchiveDao() { return new DemographicExtArchiveDaoImpl(); }
        @Bean DemographicCustDao demographicCustDao() { return new DemographicCustDaoImpl(); }
        @Bean DemographicCustArchiveDao demographicCustArchiveDao() { return new DemographicCustArchiveDaoImpl(); }
        @Bean WaitingListDao waitingListDao() { return new WaitingListDaoImpl(); }
        @Bean OscarLogDao auditLogDao() { return new OscarLogDaoImpl(); }
    }
}
