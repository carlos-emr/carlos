/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.forms.RehabStudyFormDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyEnrolment;
import io.github.carlos_emr.carlos.form.service.FormXmlImportService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.support.IntegrationTestSeedService;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The eighteen 2004 rehabilitation-study questionnaires on their real tables (issue #5693).
 *
 * <p>Runs on H2 and on the physical MariaDB lanes (generated schema, Ontario and BC Flyway).
 * Every column of every table is saved through the typed service and through the retired
 * records' writer ({@link FrmRecordHelp#saveFormRecord}, which other forms still use), and the
 * two rows must match; every column is then read back through the service and through a frozen
 * copy of the retired records' reader, and the two must match exactly. Commits and rollbacks are
 * verified from an independent connection.</p>
 *
 * @since 2026-11-26
 */
@Tag("integration")
@Tag("dao")
@Tag("form")
@ContextConfiguration(locations = "classpath:test-context-rehab-study.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RehabStudyFormDaoIntegrationTest extends CarlosTestBase {
    private static final String CHECKED = "checked='checked'";
    private static final String[] CHECKBOX_POSTS = {"on", CHECKED, "", null, "off", "ON"};
    private static final String[] DATE_POSTS = {"2024/02/29", "2024-02-29", "invalid", null};
    private static final String[] TEXT_POSTS = {"  Écho ' <synthetic> & \"q\"  ", "", null, "plain"};
    private static final int PATIENT = 695694;
    private static final int OTHER_PATIENT = PATIENT + 1;
    /** The retired writer's rows for comparison belong to a patient of their own. */
    private static final int LEGACY_PATIENT = PATIENT + 100;
    private static final int[] PATIENTS = {PATIENT, OTHER_PATIENT, LEGACY_PATIENT};

    @Autowired private RehabStudyFormService service;
    @Autowired private RehabStudyFormDao forms;
    @Autowired private OscarLogDao audit;
    @Autowired private EncounterFormDao encounterForms;
    @Autowired private FormXmlImportService importer;
    @Autowired private SecurityInfoManager security;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired @Qualifier("dataSource") private DataSource source;
    @PersistenceContext private EntityManager entityManager;
    @TempDir Path archive;
    private LoggedInInfo actor;

    static Stream<Arguments> questionnaires() {
        return RehabStudyForms.all().stream().map(type -> Arguments.of(Named.of(type.formClass(), type)));
    }

    private static RehabStudyFormType<?> type(String formClass) {
        return RehabStudyForms.forFormClass(formClass).orElseThrow();
    }

    @BeforeEach
    void prepareActor() {
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
    }

    @AfterEach
    void removeCommittedForms() throws Exception {
        LegacyJdbcQuery.releaseThreadResources();
        try (var connection = source.getConnection()) {
            for (RehabStudyFormType<?> type : RehabStudyForms.all()) {
                try (var delete = connection.prepareStatement("DELETE FROM " + type.table()
                        + " WHERE demographic_no IN (?, ?, ?)")) {
                    for (int i = 0; i < PATIENTS.length; i++) delete.setInt(i + 1, PATIENTS[i]);
                    delete.executeUpdate();
                }
            }
            try (var delete = connection.prepareStatement("DELETE FROM rehabStudy2004 WHERE demographic_no IN (?, ?, ?)")) {
                for (int i = 0; i < PATIENTS.length; i++) delete.setInt(i + 1, PATIENTS[i]);
                delete.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Every column, against the retired writer and reader
    // ------------------------------------------------------------------------------------------

    @Test
    void shouldStoreBlankIntegerAnswers_asNullWithoutLosingZeroOrBlankText() throws Exception {
        RehabStudyFormType<?> selfManagement = type("SelfManagement");
        Properties post = basePost(PATIENT);
        for (RehabStudyColumn<?> column : selfManagement.columns()) {
            if (physicalType(selfManagement, column.name()).startsWith("int(")) {
                post.setProperty(column.name(), "");
            }
        }
        post.setProperty("ex2", "  ");
        post.setProperty("ex3", "0");
        post.setProperty("ex6Spec", "");

        int id = service.saveVersion(actor, selfManagement, PATIENT, 0, post);

        Map<String, String> stored = rawRow(selfManagement.table(), PATIENT);
        for (RehabStudyColumn<?> column : selfManagement.columns()) {
            if (physicalType(selfManagement, column.name()).startsWith("int(") && !column.name().equals("ex3")) {
                assertThat(stored.get(column.name().toLowerCase(Locale.ROOT))).as(column.name()).isNull();
            }
        }
        assertThat(stored).containsEntry("ex3", "0").containsEntry("ex6spec", "");
        Properties reopened = service.load(actor, selfManagement, PATIENT, id);
        assertThat(reopened).containsEntry("ex1", "").containsEntry("ex3", "0");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionnaires")
    void shouldStoreAndShowEveryColumn_exactlyAsTheRetiredRecordDid(RehabStudyFormType<?> type)
            throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        Properties post = syntheticPost(type, PATIENT);
        int id = service.saveVersion(actor, type, PATIENT, 0, post);
        assertThat(id).isPositive();
        assertThat(independentCount(type.table(), PATIENT)).isEqualTo(1);

        Properties legacyPost = syntheticPost(type, LEGACY_PATIENT);
        if (isH2()) {
            // The retired writer looks each column up by its metadata name, which the shared H2
            // harness folds to lower case; MariaDB reports the published spelling the pages post.
            for (String name : List.copyOf(legacyPost.stringPropertyNames())) {
                legacyPost.setProperty(name.toLowerCase(Locale.ROOT), legacyPost.getProperty(name));
            }
        }
        FrmRecordHelp legacyWriter = new FrmRecordHelp();
        legacyWriter.setDateFormat("yyyy/MM/dd");
        legacyWriter.saveFormRecord(legacyPost, "SELECT * FROM " + type.table() + " WHERE demographic_no=? AND ID=0",
                Integer.toString(LEGACY_PATIENT));

        Map<String, String> stored = rawRow(type.table(), PATIENT);
        Map<String, String> legacyStored = rawRow(type.table(), LEGACY_PATIENT);
        assertThat(stored).hasSize(type.columns().size() + 6);
        for (Map<String, String> row : List.of(stored, legacyStored)) {
            row.remove("id");
            row.remove("demographic_no");
            row.remove("formedited");
        }
        assertThat(stored).as("every stored column of %s", type.table()).isEqualTo(legacyStored);

        Properties loaded = service.load(actor, type, PATIENT, id);
        assertThat(loaded).hasSize(type.columns().size() + 6);
        assertSameProperties(loaded, legacyRead(type.table(), PATIENT, id));
        assertExpectedValues(type, post, loaded, rawRow(type.table(), PATIENT));
    }

    /** Guards against the new and retired paths agreeing on a wrong value. */
    private static void assertExpectedValues(RehabStudyFormType<?> type, Properties post, Properties loaded,
            Map<String, String> stored) {
        assertThat(loaded.getProperty("provider_no")).isEqualTo("999998");
        assertThat(loaded.getProperty("formCreated")).isEqualTo("2024/02/29");
        assertThat(loaded.getProperty("studyID")).isEqualTo("S-5694");
        assertThat(loaded.getProperty("formEdited")).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        for (RehabStudyColumn<?> column : type.columns()) {
            String posted = post.getProperty(column.name());
            String shown = loaded.getProperty(column.name());
            String raw = stored.get(column.name().toLowerCase(Locale.ROOT));
            switch (column) {
                case RehabStudyColumn.Checkbox<?> c -> {
                    boolean ticked = "on".equalsIgnoreCase(posted) || CHECKED.equals(posted);
                    assertThat(raw).as("stored %s", c.name()).isIn(ticked ? List.of("1", "true") : List.of("0", "false"));
                    assertThat(shown).as("shown %s", c.name()).isEqualTo(ticked ? CHECKED : "");
                }
                case RehabStudyColumn.Day<?> d -> assertThat(shown).as("shown %s", d.name())
                        .isEqualTo(posted == null || posted.equals("invalid") ? "" : "2024/02/29");
                case RehabStudyColumn.Text<?> t -> {
                    assertThat(shown).as("shown %s", t.name()).isEqualTo(StringUtils.trimToEmpty(posted));
                    if (posted == null) {
                        assertThat(raw).as("stored %s", t.name()).isNull();
                    } else if (physicalType(type, t.name()).startsWith("varchar(")) {
                        // Text is stored as posted; only the page's display is trimmed.
                        assertThat(raw).as("stored %s", t.name()).isEqualTo(posted);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Router, ticks and identifiers
    // ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionnaires")
    void shouldKeepTicks_whenTheReopenedPageIsSavedAgain(RehabStudyFormType<?> type)
            throws Exception {
        List<String> boxes = type.columns().stream().filter(RehabStudyColumn.Checkbox.class::isInstance)
                .map(RehabStudyColumn::name).toList();
        FrmRecord routed = new FrmRecordFactory().factory(type.formClass());
        assertThat(routed).isInstanceOf(RehabStudyForm.class);
        Properties first = basePost(PATIENT);
        boxes.stream().limit(1).forEach(box -> first.setProperty(box, "on"));
        int firstId = routed.saveFormRecord(actor, first);

        // The reopened page posts "on" for each box it shows ticked and nothing for the others.
        Properties reopened = routed.getFormRecord(actor, PATIENT, firstId);
        Properties second = basePost(PATIENT);
        second.setProperty("formId", Integer.toString(firstId));
        for (String box : boxes) {
            if (CHECKED.equals(reopened.getProperty(box))) second.setProperty(box, "on");
        }
        int secondId = routed.saveFormRecord(actor, second);
        assertThat(secondId).isGreaterThan(firstId);
        Properties again = routed.getFormRecord(actor, PATIENT, secondId);
        for (String box : boxes) {
            assertThat(again.getProperty(box)).as(box).isEqualTo(reopened.getProperty(box));
        }
        if (!boxes.isEmpty()) assertThat(again.getProperty(boxes.getFirst())).isEqualTo(CHECKED);
        assertThat(independentCount(type.table(), PATIENT)).isEqualTo(2);
    }

    @Test
    void shouldKeepTheSourceAndBindTrustedIdentifiers_whenTheRouterCreatesANewVersion() throws Exception {
        FrmRecord routed = new FrmRecordFactory().factory("SF36");
        Properties post = basePost(PATIENT);
        post.setProperty("Q1Cmt", "first");
        int original = routed.saveFormRecord(actor, post);
        post.setProperty("formId", Integer.toString(original));
        post.setProperty("ID", "2147483647");
        post.setProperty("provider_no", "123456");
        post.setProperty("demographic_no", Integer.toString(PATIENT));
        post.setProperty("Q1Cmt", "second");
        int next = routed.saveFormRecord(actor, post);
        assertThat(next).isGreaterThan(original).isNotEqualTo(Integer.MAX_VALUE);
        assertThat(service.load(actor, type("SF36"), PATIENT, original).getProperty("Q1Cmt")).isEqualTo("first");
        Properties saved = service.load(actor, type("SF36"), PATIENT, next);
        assertThat(saved.getProperty("Q1Cmt")).isEqualTo("second");
        assertThat(saved.getProperty("provider_no")).isEqualTo("999998");
        assertThat(saved.getProperty("ID")).isEqualTo(Integer.toString(next));
        assertThat(independentCount("formSF36", PATIENT)).isEqualTo(2);
    }

    @Test
    void shouldRefuseForeignOrMissingVersions_withoutWritingOrExposingTheirValues() throws Exception {
        RehabStudyFormType<?> sf36 = type("SF36");
        int id = service.saveVersion(actor, sf36, PATIENT, 0, basePost(PATIENT));
        Properties post = basePost(OTHER_PATIENT);
        assertThatThrownBy(() -> service.load(actor, sf36, OTHER_PATIENT, id)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, sf36, OTHER_PATIENT, id, post))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.load(actor, sf36, PATIENT, Integer.MAX_VALUE)).isInstanceOf(SecurityException.class);
        // The view gate asks the same question before the page renders.
        assertThatThrownBy(() -> service.requireReadable(actor, sf36, OTHER_PATIENT, id)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.requireReadable(actor, sf36, PATIENT, Integer.MAX_VALUE))
                .isInstanceOf(SecurityException.class);
        service.requireReadable(actor, sf36, PATIENT, id);
        service.requireReadable(actor, sf36, OTHER_PATIENT, 0);
        // A version of another questionnaire is not a source either, even for the same patient.
        assertThatThrownBy(() -> service.saveVersion(actor, type("SF36Caregiver"), PATIENT, id, basePost(PATIENT)))
                .isInstanceOf(SecurityException.class);
        assertThat(forms.findForPatient(sf36.entity(), OTHER_PATIENT, id)).isNull();
        assertThat(independentCount("formSF36", PATIENT)).isEqualTo(1);
        assertThat(independentCount("formSF36", OTHER_PATIENT)).isZero();
        assertThat(independentCount("formSF36Caregiver", PATIENT)).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------------------------------

    @Test
    void shouldRollBackEarlierFormWrites_whenALaterVersionIsRefused() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        var first = new AtomicInteger();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            first.set(service.saveVersion(actor, type("SF36"), PATIENT, 0, basePost(PATIENT)));
            assertThat(first.get()).isPositive();
            service.saveVersion(actor, type("IntakeInfo"), PATIENT, 0, basePost(PATIENT));
            service.saveVersion(actor, type("SF36"), OTHER_PATIENT, first.get(), basePost(OTHER_PATIENT));
        })).isInstanceOf(SecurityException.class);
        assertThat(first.get()).isPositive();
        assertThat(independentCount("formSF36", PATIENT)).isZero();
        assertThat(independentCount("formIntakeInfo", PATIENT)).isZero();
        assertThat(forms.findForPatient(type("SF36").entity(), PATIENT, first.get())).isNull();
    }

    @Test
    void shouldCommitRelatedVersions_whenTheOuterTransactionSucceeds() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            int first = service.saveVersion(actor, type("SF36"), PATIENT, 0, basePost(PATIENT));
            service.saveVersion(actor, type("SF36"), PATIENT, first, basePost(PATIENT));
            service.saveVersion(actor, type("Falls"), PATIENT, 0, basePost(PATIENT));
        });
        assertThat(independentCount("formSF36", PATIENT)).isEqualTo(2);
        assertThat(independentCount("formFalls", PATIENT)).isEqualTo(1);
    }

    @Test
    void shouldRollBackAnEarlierInsert_whenARelatedDatabaseWriteFails() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        // Record the first save's ID: a save that threw by itself would also leave no rows.
        var first = new AtomicInteger();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            first.set(service.saveVersion(actor, type("CESD"), PATIENT, 0, basePost(PATIENT)));
            entityManager.createNativeQuery("INSERT INTO formCESD (demographic_no, studyID, formEdited)"
                    + " VALUES (NULL, 'N/A', CURRENT_TIMESTAMP)").executeUpdate();
        })).isInstanceOf(RuntimeException.class).isNotInstanceOf(SecurityException.class);
        assertThat(first.get()).isPositive();
        assertThat(independentCount("formCESD", PATIENT)).isZero();
    }

    @Test
    void shouldRollBackTheGeneratedInsert_whenTheServiceFailsAfterFlushing() throws Exception {
        RehabStudyFormService target = AopTestUtils.getUltimateTargetObject(service);
        RehabStudyFormDao failing = mock(RehabStudyFormDao.class, AdditionalAnswers.delegatesTo(forms));
        doAnswer(call -> {
            forms.flush();
            throw new IllegalStateException("synthetic failure after insert");
        }).when(failing).flush();
        try {
            ReflectionTestUtils.setField(target, "forms", failing);
            Properties post = basePost(PATIENT);
            assertThatThrownBy(() -> service.saveVersion(actor, type("GripStrength"), PATIENT, 0, post))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(independentCount("formGripStrength", PATIENT)).isZero();
        } finally {
            ReflectionTestUtils.setField(target, "forms", forms);
        }
    }

    @Test
    void shouldRefuseAndWriteNothing_whenThePostHasNoStudyId() throws Exception {
        Properties post = basePost(PATIENT);
        post.remove("studyID");
        assertThatThrownBy(() -> service.saveVersion(actor, type("Falls"), PATIENT, 0, post))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(independentCount("formFalls", PATIENT)).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // New-form prefill and audit
    // ------------------------------------------------------------------------------------------

    @Test
    void shouldPrefillAndStoreTheMasterRecordAndLowestStudyId_whenStartingIntakeInformation() throws Exception {
        try (var connection = source.getConnection();
             var insert = connection.prepareStatement("INSERT INTO rehabStudy2004 (studyID, demographic_no) VALUES (?, ?)")) {
            for (int studyId : new int[]{9_569_401, 9_569_377}) {
                insert.setInt(1, studyId);
                insert.setInt(2, PATIENT);
                insert.executeUpdate();
            }
        }
        // The read-only enrolment mapping reads the physical row by its studyID key.
        RehabStudyEnrolment enrolment = entityManager.find(RehabStudyEnrolment.class, 9_569_377);
        assertThat(enrolment.getId()).isEqualTo(9_569_377);
        assertThat(enrolment.getDemographicNo()).isEqualTo(PATIENT);
        DemographicManager demographics = mock(DemographicManager.class);
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setSex(" F ");
        patient.setYearOfBirth("1950");
        patient.setMonthOfBirth("02");
        patient.setDateOfBirth("28");
        patient.setPhone(" 555-0100 ");
        when(demographics.getDemographic(any(), eq(PATIENT))).thenReturn(patient);
        RehabStudyFormService target = AopTestUtils.getUltimateTargetObject(service);
        Object previous = ReflectionTestUtils.getField(target, "demographics");
        try {
            ReflectionTestUtils.setField(target, "demographics", demographics);
            Properties fresh = service.load(actor, type("IntakeInfo"), PATIENT, 0);
            assertThat(fresh.getProperty("studyID")).isEqualTo("9569377");
            assertThat(fresh.getProperty("sex")).isEqualTo("F");
            assertThat(fresh.getProperty("dob")).isEqualTo("1950/02/28");
            assertThat(fresh.getProperty("phone")).isEqualTo("555-0100");
            assertThat(service.load(actor, type("IntakeInfo"), OTHER_PATIENT, 0).getProperty("studyID")).isEqualTo("N/A");

            // The page posts the prefill back; Intake Information stores it in its own columns.
            fresh.setProperty("demographic_no", Integer.toString(PATIENT));
            int id = service.saveVersion(actor, type("IntakeInfo"), PATIENT, 0, fresh);
            Map<String, String> stored = rawRow("formIntakeInfo", PATIENT);
            assertThat(stored.get("sex")).isEqualTo("F");
            assertThat(stored.get("dob")).isEqualTo("1950-02-28");
            assertThat(stored.get("phone")).isEqualTo("555-0100");
            assertThat(stored.get("studyid")).isEqualTo("9569377");
            assertThat(service.load(actor, type("IntakeInfo"), PATIENT, id).getProperty("dob")).isEqualTo("1950/02/28");
        } finally {
            ReflectionTestUtils.setField(target, "demographics", previous);
        }
    }

    @Test
    void shouldCommitTheDemographicAudit_whenStartingANewForm() throws Exception {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(PATIENT);
        demographic.setFirstName("Synthetic");
        demographic.setLastName("Écho");
        demographic.setSex("F");
        demographic.setYearOfBirth("1950");
        demographic.setMonthOfBirth("02");
        demographic.setDateOfBirth("28");
        DemographicDao demographicDao = mock(DemographicDao.class);
        when(demographicDao.getDemographicById(PATIENT)).thenReturn(demographic);
        // Exercise the real getDemographic -> getMRP -> getHealthCareTeam -> LogAction path.
        DemographicManagerImpl manager = new DemographicManagerImpl();
        ReflectionTestUtils.setField(manager, "demographicDao", demographicDao);
        ReflectionTestUtils.setField(manager, "demographicContactDao", mock(DemographicContactDao.class));
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        ReflectionTestUtils.setField(manager, "appointmentManager", mock(AppointmentManager.class));
        RehabStudyFormService target = AopTestUtils.getUltimateTargetObject(service);
        Object previousDemographics = ReflectionTestUtils.getField(target, "demographics");
        Object previousAudit = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        try (LogCapture logs = LogCapture.forLogger(LogAction.class)) {
            ReflectionTestUtils.setField(target, "demographics", manager);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", audit);
            Properties loaded = service.load(actor, type("SelfAdministered"), PATIENT, 0);
            assertThat(loaded.getProperty("sex")).isEqualTo("F");
            assertThat(loaded.getProperty("dob")).isEqualTo("1950/02/28");
            assertThat(loaded.getProperty("phone")).as("Self-Administered never showed the phone").isNull();
            assertThat(logs.events()).isEmpty();
            // Prove the audit committed; a successful return alone could hide a swallowed write failure.
            try (var connection = source.getConnection(); var query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM log WHERE action = ? AND data = ?")) {
                query.setString(1, "DemographicManager.getHealthCareTeam");
                query.setString(2, Integer.toString(PATIENT));
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            ReflectionTestUtils.setField(target, "demographics", previousDemographics);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousAudit);
            try (var connection = source.getConnection(); var delete = connection.prepareStatement(
                    "DELETE FROM log WHERE action = ? AND data = ?")) {
                delete.setString(1, "DemographicManager.getHealthCareTeam");
                delete.setString(2, Integer.toString(PATIENT));
                delete.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Physical metadata
    // ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionnaires")
    void shouldPresentThePublishedColumnTypes_toTheLegacyReaders(RehabStudyFormType<?> type)
            throws Exception {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT * FROM " + type.table() + " WHERE 1 = 0");
             var rows = query.executeQuery()) {
            var metadata = rows.getMetaData();
            assertThat(metadata.getColumnCount()).isEqualTo(type.columns().size() + 6);
            Map<String, String> typeNames = new LinkedHashMap<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                typeNames.put(metadata.getColumnName(column).toLowerCase(Locale.ROOT), metadata.getColumnTypeName(column));
            }
            assertThat(typeNames.get("formedited")).isEqualToIgnoringCase("TIMESTAMP");
            assertThat(typeNames.get("formcreated")).isEqualToIgnoringCase("DATE");
            for (RehabStudyColumn<?> column : type.columns()) {
                String typeName = typeNames.get(column.name().toLowerCase(Locale.ROOT));
                switch (column) {
                    case RehabStudyColumn.Checkbox<?> c ->
                            assertThat(FrmRecordHelp.isBooleanColumnType(typeName)).as("%s %s", c.name(), typeName).isTrue();
                    case RehabStudyColumn.Day<?> d -> assertThat(typeName).as(d.name()).isEqualToIgnoringCase("DATE");
                    case RehabStudyColumn.Text<?> t ->
                            assertThat(FrmRecordHelp.isBooleanColumnType(typeName)).as("%s %s", t.name(), typeName).isFalse();
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Optional XML archive
    // ------------------------------------------------------------------------------------------

    @Test
    void shouldArchiveAtTheStoredEditTimes_whenTwoSavesOverlap() throws Exception {
        RehabStudyFormType<?> type = type("SF36");
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        int previousId = transaction.execute(status -> {
            IntegrationTestSeedService.ensureDemographicExists(entityManager, PATIENT);
            Date previousEdited = new Date(FormXmlArchive.currentSecond().getTime() + 30_000L);
            var previous = RehabStudyFormValues.fromProperties(type, basePost(PATIENT), previousEdited);
            previous.setDemographicNo(PATIENT);
            forms.persist(previous);
            forms.flush();
            return previous.getId();
        });

        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        configuration.setProperty("save_as_xml", "true");
        configuration.setProperty("form_record_path", archive.toString());
        var firstInserted = new CompletableFuture<Void>();
        var allowCommit = new CompletableFuture<Void>();
        var secondStarted = new CompletableFuture<Void>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction.execute(status -> {
                int id = service.saveVersion(actor, type, PATIENT, previousId, basePost(PATIENT));
                firstInserted.complete(null);
                allowCommit.orTimeout(10, TimeUnit.SECONDS).join();
                return id;
            }));
            var second = executor.submit(() -> {
                firstInserted.get(10, TimeUnit.SECONDS);
                return transaction.execute(status -> {
                    secondStarted.complete(null);
                    return service.saveVersion(actor, type, PATIENT, previousId, basePost(PATIENT));
                });
            });
            try {
                secondStarted.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                        .as("the second save waits for the first commit")
                        .isInstanceOf(TimeoutException.class);
            } finally {
                allowCommit.complete(null);
            }
            int firstId = first.get(10, TimeUnit.SECONDS);
            int secondId = second.get(10, TimeUnit.SECONDS);

            Date firstEdited = forms.findForPatient(type.entity(), PATIENT, firstId).getFormEdited();
            Date secondEdited = forms.findForPatient(type.entity(), PATIENT, secondId).getFormEdited();
            assertThat(secondEdited).isAfter(firstEdited);
            for (int id : List.of(firstId, secondId)) {
                Date edited = forms.findForPatient(type.entity(), PATIENT, id).getFormEdited();
                Path file = archive.resolve(type.table() + "_" + PATIENT + "_" + stamp(edited) + ".xml");
                var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(file.toFile());
                assertThat(caseInsensitive(xmlColumns(document))).containsEntry("id", Integer.toString(id));
            }
        } finally {
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
            transaction.executeWithoutResult(status -> entityManager.createNativeQuery(
                    "DELETE FROM demographic WHERE demographic_no = ?1").setParameter(1, PATIENT).executeUpdate());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2MinWalk", "SF36Caregiver"})
    void shouldArchiveOnlyCommittedRows_withTheXmlImportContract(String formClass) throws Exception {
        RehabStudyFormType<?> type = type(formClass);
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        try {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            var transaction = new TransactionTemplate(transactions);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                service.saveVersion(actor, type, PATIENT, 0, syntheticPost(type, PATIENT));
                throw new IllegalStateException("synthetic failure after insert");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(independentCount(type.table(), PATIENT)).isZero();
            try (var files = Files.list(archive)) {
                assertThat(files).isEmpty();
            }

            int id = service.saveVersion(actor, type, PATIENT, 0, syntheticPost(type, PATIENT));
            try (var files = Files.list(archive)) {
                List<Path> saved = files.toList();
                assertThat(saved).hasSize(1);
                Path file = saved.getFirst();
                var target = JDBCUtil.parseImportFileName(file.getFileName().toString());
                assertThat(target.formName()).isEqualTo(type.table());
                assertThat(target.demographicNo()).isEqualTo(Integer.toString(PATIENT));
                // Named for the version's stored edit time, the restored row's identity, not the commit clock.
                assertThat(target.timeStamp()).isEqualTo(stamp(forms.findForPatient(type.entity(), PATIENT, id).getFormEdited()));
                var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(file.toFile());
                Map<String, String> columns = xmlColumns(document);
                assertThat(columns).isEqualTo(xmlColumns(forms.readArchive(type.entity(), PATIENT, id)));
                assertThat(columns).hasSize(type.columns().size() + 6);
                assertThat(columns.get(isH2() ? "id" : "ID")).isEqualTo(Integer.toString(id));
            }
        } finally {
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRetainTheCommittedFormAndLogSafely_whenTheArchiveReadOrWriteFails(boolean failRead) throws Exception {
        RehabStudyFormType<?> type = type("Caregiver");
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        RehabStudyFormService target = AopTestUtils.getUltimateTargetObject(service);
        RehabStudyFormDao archiveReader = mock(RehabStudyFormDao.class, AdditionalAnswers.delegatesTo(forms));
        if (failRead) {
            doAnswer(call -> {
                // An independent connection must see the form before the archive read.
                assertThat(independentCount(type.table(), PATIENT)).isEqualTo(1);
                throw new IllegalStateException("synthetic sensitive archive failure: Écho");
            }).when(archiveReader).readArchive(eq(type.entity()), eq(PATIENT), anyInt());
        }
        // A regular file cannot be used as an archive directory, even when tests run as root.
        Path archivePath = failRead ? archive : Files.createFile(archive.resolve("not-a-directory"));
        // MiscUtils names the logger after the afterCommit callback's anonymous class.
        try (LogCapture logs = LogCapture.forLogger(RehabStudyFormService.class.getName() + "$1")) {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archivePath.toString());
            ReflectionTestUtils.setField(target, "forms", archiveReader);
            int id = service.saveVersion(actor, type, PATIENT, 0, syntheticPost(type, PATIENT));
            assertThat(id).isPositive();
            assertThat(independentCount(type.table(), PATIENT)).isEqualTo(1);
            assertSameProperties(service.load(actor, type, PATIENT, id), legacyRead(type.table(), PATIENT, id));
            verify(archiveReader).readArchive(type.entity(), PATIENT, id);
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .isEqualTo("Unable to archive committed rehabilitation study form XML (%s); continuing without XML copy",
                                failRead ? "IllegalStateException" : "FileSystemException")
                        .doesNotContain("Écho", Integer.toString(PATIENT), archivePath.toString());
                assertThat(event.getThrown()).isNull();
            });
            try (var files = Files.list(archive)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".xml"))).isEmpty();
            }
        } finally {
            ReflectionTestUtils.setField(target, "forms", forms);
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
        }
    }

    /**
     * Two versions of one questionnaire committed in one transaction keep an XML copy each, and
     * both restore. The import takes a version's patient and edit time from the archive name and
     * skips one it already holds, so the second version's edit time follows the first's and each
     * copy is named for its own version's edit time (#5693 review: both callbacks used to name the
     * copy from the commit clock, so the second replaced the first).
     */
    @Test
    void shouldKeepAndRestoreEachVersionsArchive_whenTwoVersionsCommitTogether() throws Exception {
        RehabStudyFormType<?> type = type("SF36");
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        EncounterForm registration = null;
        try {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            Properties first = syntheticPost(type, PATIENT);
            first.setProperty("Q1Cmt", "FAKE first version");
            Properties second = syntheticPost(type, PATIENT);
            second.setProperty("Q1Cmt", "FAKE second version");
            int[] ids = new TransactionTemplate(transactions).execute(status -> new int[] {
                service.saveVersion(actor, type, PATIENT, 0, first), service.saveVersion(actor, type, PATIENT, 0, second)});
            java.util.Date firstEdited = forms.findForPatient(type.entity(), PATIENT, ids[0]).getFormEdited();
            java.util.Date secondEdited = forms.findForPatient(type.entity(), PATIENT, ids[1]).getFormEdited();
            assertThat(secondEdited).isAfter(firstEdited);

            Map<String, String> answers = Map.of(stamp(firstEdited), "FAKE first version",
                    stamp(secondEdited), "FAKE second version");
            Map<String, Path> archives = new TreeMap<>();
            try (var files = Files.list(archive)) {
                files.forEach(file -> archives.put(file.getFileName().toString(), file));
            }
            assertThat(archives.keySet()).containsExactly(type.table() + "_" + PATIENT + "_" + stamp(firstEdited) + ".xml",
                    type.table() + "_" + PATIENT + "_" + stamp(secondEdited) + ".xml");
            int index = 0;
            for (Path file : archives.values()) {
                Map<String, String> columns = caseInsensitive(xmlColumns(
                        XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(file.toFile())));
                assertThat(columns.get("id")).isEqualTo(Integer.toString(ids[index++]));
                assertThat(columns.get("q1cmt")).isEqualTo(answers.get(JDBCUtil.parseImportFileName(
                        file.getFileName().toString()).timeStamp()));
            }

            // Restore both from their copies alone.
            if (encounterForms.findByFormTable(type.table()).isEmpty()) {
                registration = new EncounterForm();
                registration.setFormName("FAKE " + type.table());
                registration.setFormTable(type.table());
                registration.setFormValue("../form/FAKE-archive-restore?table=" + type.table());
                EncounterForm registered = registration;
                new TransactionTemplate(transactions).executeWithoutResult(status -> encounterForms.persist(registered));
            }
            deleteIndependently(type.table(), PATIENT);
            if (isH2()) {
                // H2 cannot read the archive name's yyyyMMddHHmmss edit time as a timestamp (MariaDB, the
                // server CARLOS ships with, can), so here the importer's parse of both copies is checked.
                for (Path file : archives.values()) {
                    try (var input = Files.newInputStream(file)) {
                        var request = JDBCUtil.readImportRequest(input, file.getFileName().toString());
                        assertThat(request.target().demographicNo()).isEqualTo(Integer.toString(PATIENT));
                        assertThat(caseInsensitive(request.values()).get("q1cmt"))
                                .isEqualTo(answers.get(request.target().timeStamp()));
                    }
                }
            } else {
                for (Path file : archives.values()) {
                    try (var input = Files.newInputStream(file)) {
                        assertThat(importer.importEntry(input, file.getFileName().toString())).isNotNull();
                    }
                }
                Map<String, String> restored = new TreeMap<>();
                try (var connection = source.getConnection();
                     var query = connection.prepareStatement("SELECT formEdited, Q1Cmt FROM " + type.table()
                             + " WHERE demographic_no = ?")) {
                    query.setInt(1, PATIENT);
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) restored.put(stamp(rows.getTimestamp(1)), rows.getString(2));
                    }
                }
                assertThat(restored).isEqualTo(new TreeMap<>(answers));
                // A copy whose version is already held is skipped, not duplicated.
                for (Path file : archives.values()) {
                    try (var input = Files.newInputStream(file)) {
                        assertThat(importer.importEntry(input, file.getFileName().toString())).isNull();
                    }
                }
                assertThat(independentCount(type.table(), PATIENT)).isEqualTo(2);
            }
        } finally {
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
            if (registration != null && registration.getId() != null) {
                String registered = registration.getId();
                new TransactionTemplate(transactions).executeWithoutResult(status -> encounterForms.remove(registered));
            }
        }
    }

    /**
     * The XML copy is read after commit with the save's own, still-held connection. A separate
     * transaction would hold a second pooled connection per archived save, so concurrent saves
     * could exhaust the pool; here the pool can lend exactly one connection, to the save itself.
     */
    @Test
    void shouldArchiveWithTheSavesOwnConnection_whenThePoolHasNoSpareConnection() throws Exception {
        RehabStudyFormType<?> type = type("TreatmentPref");
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        BasicDataSource pool = (BasicDataSource) source;
        int previousMax = pool.getMaxTotal();
        java.time.Duration previousWait = pool.getMaxWaitDuration();
        List<java.sql.Connection> held = new java.util.ArrayList<>();
        int id;
        try (LogCapture logs = LogCapture.forLogger(RehabStudyFormService.class.getName() + "$1")) {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            pool.setMaxWait(java.time.Duration.ofSeconds(2));
            // Hold every idle connection, then allow the pool exactly one more.
            int idle = pool.getNumIdle();
            pool.setMaxTotal(pool.getNumActive() + idle + 1);
            for (int i = 0; i < idle; i++) held.add(pool.getConnection());
            id = service.saveVersion(actor, type, PATIENT, 0, syntheticPost(type, PATIENT));
            assertThat(logs.events()).as("archive failures").isEmpty();
        } finally {
            for (java.sql.Connection connection : held) connection.close();
            pool.setMaxTotal(previousMax);
            pool.setMaxWait(previousWait);
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
        }
        assertThat(id).isPositive();
        assertThat(independentCount(type.table(), PATIENT)).isEqualTo(1);
        try (var files = Files.list(archive)) {
            List<Path> saved = files.toList();
            assertThat(saved).hasSize(1);
            var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(saved.getFirst().toFile());
            assertThat(xmlColumns(document).get(isH2() ? "id" : "ID")).isEqualTo(Integer.toString(id));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static Properties basePost(int patient) {
        Properties post = new Properties();
        post.setProperty("demographic_no", Integer.toString(patient));
        post.setProperty("provider_no", "999998");
        post.setProperty("formCreated", "2024/02/29");
        post.setProperty("studyID", "S-5694");
        return post;
    }

    /** A post that gives every column of the questionnaire a value its physical type accepts. */
    private static Properties syntheticPost(RehabStudyFormType<?> type, int patient) {
        Properties post = basePost(patient);
        int index = 0;
        for (RehabStudyColumn<?> column : type.columns()) {
            String physical = physicalType(type, column.name());
            String value = switch (column) {
                case RehabStudyColumn.Checkbox<?> _ -> CHECKBOX_POSTS[index % CHECKBOX_POSTS.length];
                case RehabStudyColumn.Day<?> _ -> DATE_POSTS[index % DATE_POSTS.length];
                case RehabStudyColumn.Text<?> _ -> textPost(physical, index);
            };
            if (value != null) post.setProperty(column.name(), value);
            index++;
        }
        return post;
    }

    private static String textPost(String physical, int index) {
        if (physical.startsWith("int(")) return index % 5 == 4 ? null : Integer.toString(index % 10);
        if (physical.equals("double")) return index % 3 == 2 ? null : index + ".5";
        int width = Integer.parseInt(physical.substring(physical.indexOf('(') + 1, physical.indexOf(')')));
        String value = TEXT_POSTS[index % TEXT_POSTS.length];
        if (value == null || value.length() <= width) return value;
        // Short answer columns hold codes such as "Y"; keep surrounding spaces where they fit.
        String code = "Yes".substring(0, Math.min(3, Math.max(1, width - 2)));
        return width >= code.length() + 2 ? " " + code + " " : code;
    }

    private static String physicalType(RehabStudyFormType<?> type, String column) {
        // The SF-36 entities declare their answer fields in the shared SF36Answers superclass.
        return Stream.<Class<?>>iterate(type.entity(), c -> c != RehabStudyAssessment.class, Class::getSuperclass)
                .flatMap(c -> Stream.of(c.getDeclaredFields())).map(f -> f.getAnnotation(Column.class))
                .filter(c -> c != null && c.name().equals(column)).findFirst().orElseThrow().columnDefinition();
    }

    /** Every column of the patient's single row, as the JDBC driver reports it, keyed in lower case. */
    private Map<String, String> rawRow(String table, int patient) throws SQLException {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT * FROM " + table + " WHERE demographic_no = ?")) {
            query.setInt(1, patient);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).as("row of %s for %s", table, patient).isTrue();
                var metadata = rows.getMetaData();
                Map<String, String> row = new TreeMap<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.put(metadata.getColumnName(column).toLowerCase(Locale.ROOT), rows.getString(column));
                }
                assertThat(rows.next()).as("single row of %s for %s", table, patient).isFalse();
                return row;
            }
        }
    }

    /**
     * The retired records' reader, frozen: every rehabilitation-study {@code Frm*Record} read a
     * saved version with this loop before issue #5693.
     */
    private Properties legacyRead(String table, int patient, int id) throws SQLException {
        Properties props = new Properties();
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT * FROM " + table + " WHERE demographic_no = ? AND ID = ?")) {
            query.setInt(1, patient);
            query.setInt(2, id);
            try (var rs = query.executeQuery()) {
                if (rs.next()) {
                    var md = rs.getMetaData();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String value;
                        if (FrmRecordHelp.isBooleanColumnType(md.getColumnTypeName(i))) {
                            value = rs.getInt(i) == 1 ? CHECKED : "";
                        } else if (md.getColumnTypeName(i).equalsIgnoreCase("date")) {
                            value = UtilDateUtilities.DateToString(rs.getDate(i), "yyyy/MM/dd");
                        } else {
                            value = Misc.getString(rs, i);
                        }
                        if (value != null) props.setProperty(md.getColumnName(i), value);
                    }
                }
            }
        }
        return props;
    }

    private void assertSameProperties(Properties loaded, Properties legacy) throws Exception {
        if (isH2()) {
            // The shared H2 harness folds unquoted identifiers to lower case. MariaDB keeps the
            // published column spelling, which must match the pages' field names exactly.
            assertThat(foldColumnNames(loaded)).isEqualTo(foldColumnNames(legacy));
        } else {
            assertThat(loaded).isEqualTo(legacy);
        }
    }

    private static Map<String, String> foldColumnNames(Properties values) {
        Map<String, String> result = new TreeMap<>();
        values.forEach((key, value) -> result.put(key.toString().toLowerCase(Locale.ROOT), value.toString()));
        assertThat(result).hasSize(values.size());
        return result;
    }

    private static Map<String, String> xmlColumns(org.w3c.dom.Document document) {
        Map<String, String> result = new TreeMap<>();
        var columns = document.getElementsByTagName("Row").item(0).getChildNodes();
        for (int index = 0; index < columns.getLength(); index++) {
            var column = columns.item(index);
            if (column.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                result.put(column.getNodeName(), column.getTextContent());
            }
        }
        return result;
    }

    private static String stamp(java.util.Date edited) {
        return UtilDateUtilities.DateToString(edited, "yyyyMMddHHmmss");
    }

    /** XML and import column names are upper-case on MariaDB and lower-case on H2. */
    private static Map<String, String> caseInsensitive(Map<String, String> columns) {
        Map<String, String> folded = new TreeMap<>();
        columns.forEach((name, value) -> folded.put(name.toLowerCase(Locale.ROOT), value));
        return folded;
    }

    private void deleteIndependently(String table, int patient) throws Exception {
        try (var connection = source.getConnection();
             var delete = connection.prepareStatement("DELETE FROM " + table + " WHERE demographic_no = ?")) {
            delete.setInt(1, patient);
            delete.executeUpdate();
        }
    }

    private static void restore(Properties properties, String key, String value) {
        if (value == null) properties.remove(key); else properties.setProperty(key, value);
    }

    private boolean isH2() throws Exception {
        try (var connection = source.getConnection()) {
            return "H2".equals(connection.getMetaData().getDatabaseProductName());
        }
    }

    private int independentCount(String table, int patient) throws Exception {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE demographic_no = ?")) {
            query.setInt(1, patient);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

}
