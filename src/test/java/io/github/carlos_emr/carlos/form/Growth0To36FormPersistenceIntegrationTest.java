/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormGrowth0To36Dao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The same suite runs on H2 and the opt-in physical MariaDB/Flyway test lane. */
@Tag("integration")
@Tag("form")
@ContextConfiguration(locations = "classpath:test-context-growth.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Growth0To36FormPersistenceIntegrationTest extends CarlosTestBase {
    @Autowired private Growth0To36FormService service;
    @Autowired private FormGrowth0To36Dao forms;
    @Autowired private OscarLogDao audit;
    @Autowired private SecurityInfoManager security;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired @Qualifier("dataSource") private DataSource source;
    @PersistenceContext private EntityManager entityManager;
    @TempDir Path archive;
    private final int patient = 695693;
    private LoggedInInfo actor;
    private Properties values;

    @BeforeEach
    void prepareForm() {
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        values = new Properties();
        values.setProperty("demographic_no", Integer.toString(patient));
        values.setProperty("provider_no", "999998");
        values.setProperty("formCreated", "2024/02/29");
        values.setProperty("patientName", "Synthetic Écho");
        values.setProperty("recordNo", "G5693");
        values.setProperty("motherStature", "165.0");
        values.setProperty("fatherStature", "180.0");
        values.setProperty("gestationalAge", "39");
        values.setProperty("edc", "2024/02/29");
        for (int slot = 1; slot <= 20; slot++) {
            values.setProperty("date_" + slot, slot % 2 == 0 ? "2024-02-29" : "2024/02/29");
            values.setProperty("age_" + slot, slot + "m");
            values.setProperty("weight_" + slot, (slot + 3) + ".51");
            values.setProperty("length_" + slot, (slot + 52) + ".2");
            values.setProperty("headCirc_" + slot, (slot + 34) + ".2");
            values.setProperty("comment_" + slot, "Écho ' <synthetic> " + slot);
        }
    }

    @AfterEach
    void removeCommittedForms() throws Exception {
        LegacyJdbcQuery.releaseThreadResources();
        try (var connection = source.getConnection();
             var delete = connection.prepareStatement("DELETE FROM formGrowth0_36 WHERE demographic_no IN (?, ?)")) {
            delete.setInt(1, patient);
            delete.setInt(2, patient + 1);
            delete.executeUpdate();
        }
    }

    @Test
    void shouldRoundTripEveryMappedColumn_withTheLegacyPropertiesContract() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        int id = service.saveVersion(actor, patient, 0, values);
        assertThat(id).isPositive();
        assertThat(independentCount()).isEqualTo(1);
        Properties loaded = service.load(actor, patient, id);
        assertLegacyProperties(id, loaded);
        assertThat(loaded.getProperty("patientName")).isEqualTo(values.getProperty("patientName"));
        for (int slot = 1; slot <= 20; slot++) {
            assertThat(loaded.getProperty("date_" + slot)).isEqualTo("2024/02/29");
            for (String field : new String[]{"age", "weight", "length", "headCirc", "comment"}) {
                assertThat(loaded.getProperty(field + "_" + slot)).isEqualTo(values.getProperty(field + "_" + slot));
            }
        }
        assertThat(forms.findForPatient(patient, id).getProviderNo()).isEqualTo(999998);
        assertThat(forms.findForPatient(patient + 1, id)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldCommitDemographicAudit_whenLoadingWithoutAnMrp(boolean savedVersion) throws Exception {
        int id = savedVersion ? service.saveVersion(actor, patient, 0, values) : 0;
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(patient);
        demographic.setFirstName("Synthetic");
        demographic.setLastName("Écho");
        demographic.setSex("F");
        demographic.setYearOfBirth("2024");
        demographic.setMonthOfBirth("02");
        demographic.setDateOfBirth("29");
        DemographicDao demographicDao = mock(DemographicDao.class);
        when(demographicDao.getDemographicById(patient)).thenReturn(demographic);
        // Exercise the real getDemographic -> getMRP -> getHealthCareTeam -> LogAction path.
        DemographicManagerImpl manager = new DemographicManagerImpl();
        ReflectionTestUtils.setField(manager, "demographicDao", demographicDao);
        ReflectionTestUtils.setField(manager, "demographicContactDao", mock(DemographicContactDao.class));
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        ReflectionTestUtils.setField(manager, "appointmentManager", mock(AppointmentManager.class));
        Growth0To36FormService target = AopTestUtils.getUltimateTargetObject(service);
        Object previousDemographics = ReflectionTestUtils.getField(target, "demographics");
        Object previousAudit = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        try (LogCapture logs = LogCapture.forLogger(LogAction.class)) {
            ReflectionTestUtils.setField(target, "demographics", manager);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", audit);
            Properties loaded = service.load(actor, patient, id);
            assertThat(loaded.getProperty("patientName")).isEqualTo("Synthetic Écho");
            assertThat(loaded.getProperty("patientSex")).isEqualTo("F");
            assertThat(loaded.getProperty("dateOfBirth")).isEqualTo("2024/02/29");
            assertThat(logs.events()).isEmpty();
            // Prove the audit committed; a successful return alone could hide a swallowed write failure.
            try (var connection = source.getConnection(); var query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM log WHERE action = ? AND data = ?")) {
                query.setString(1, "DemographicManager.getHealthCareTeam");
                query.setString(2, Integer.toString(patient));
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
            assertThat(independentCount()).isEqualTo(savedVersion ? 1 : 0);
        } finally {
            ReflectionTestUtils.setField(target, "demographics", previousDemographics);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousAudit);
            try (var connection = source.getConnection(); var delete = connection.prepareStatement(
                    "DELETE FROM log WHERE action = ? AND data = ?")) {
                delete.setString(1, "DemographicManager.getHealthCareTeam");
                delete.setString(2, Integer.toString(patient));
                delete.executeUpdate();
            }
        }
    }

    @Test
    void shouldPreservePublishedColumnMetadata_whenUsingThePhysicalTable() throws Exception {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT * FROM formGrowth0_36 WHERE 1 = 0");
             var rows = query.executeQuery()) {
            var metadata = rows.getMetaData();
            assertThat(metadata.getColumnCount()).isEqualTo(131);
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if ("formEdited".equalsIgnoreCase(metadata.getColumnName(column))) {
                    assertThat(metadata.getColumnTypeName(column)).isEqualToIgnoringCase("TIMESTAMP");
                }
            }
        }
    }

    @Test
    void shouldTrimDisplayValuesButRetainStoredText_whenWhitespaceWasSubmitted() throws Exception {
        values.setProperty("patientName", "  Synthetic Écho  ");
        values.setProperty("weight_1", " 4.51 ");
        values.setProperty("comment_20", "  Écho ' <synthetic>  ");
        int id = service.saveVersion(actor, patient, 0, values);
        var stored = forms.findForPatient(patient, id);
        assertThat(stored.getPatientName()).isEqualTo(values.getProperty("patientName"));
        assertThat(stored.getMeasurement(20).getComment()).isEqualTo(values.getProperty("comment_20"));
        Properties loaded = service.load(actor, patient, id);
        assertThat(loaded.getProperty("patientName")).isEqualTo("Synthetic Écho");
        assertThat(loaded.getProperty("weight_1")).isEqualTo("4.51");
        assertThat(loaded.getProperty("comment_20")).isEqualTo("Écho ' <synthetic>");
        assertLegacyProperties(id, loaded);
    }

    @Test
    void shouldDisplayEmptyValues_whenAnEntireLegacySlotIsNull() throws Exception {
        for (String field : new String[]{"date", "age", "weight", "length", "headCirc", "comment"}) {
            values.remove(field + "_20");
        }
        values.setProperty("date_1", "invalid");
        int id = service.saveVersion(actor, patient, 0, values);
        Properties loaded = service.load(actor, patient, id);
        for (String field : new String[]{"date", "age", "weight", "length", "headCirc", "comment"}) {
            assertThat(loaded.getProperty(field + "_20")).isEmpty();
        }
        assertThat(loaded.getProperty("date_1")).isEmpty();
        assertLegacyProperties(id, loaded);
    }

    @Test
    void shouldRetainNullsAndEmptyStrings_whenFieldsAreMissingOrBlank() throws Exception {
        values.remove("motherStature");
        values.remove("date_1");
        values.setProperty("date_2", "");
        values.setProperty("weight_1", "");
        values.remove("comment_20");
        int id = service.saveVersion(actor, patient, 0, values);
        var loaded = forms.findForPatient(patient, id);
        assertThat(loaded.getMotherStature()).isNull();
        assertThat(loaded.getMeasurement(1).getDate()).isNull();
        assertThat(loaded.getMeasurement(2).getDate()).isNull();
        assertThat(loaded.getMeasurement(1).getWeight()).isEmpty();
        assertThat(loaded.getMeasurement(20).getComment()).isNull();
        assertLegacyProperties(id, service.load(actor, patient, id));
    }

    @Test
    void shouldKeepSourceAndBindTrustedIdentifiers_whenRouterCreatesANewVersion() throws Exception {
        FrmRecord routed = new FrmRecordFactory().factory("Growth0_36");
        assertThat(routed).isInstanceOf(Growth0To36Form.class);
        int original = routed.saveFormRecord(actor, values);
        values.setProperty("formId", Integer.toString(original));
        values.setProperty("ID", "2147483647");
        values.setProperty("provider_no", "123456");
        values.setProperty("weight_1", "6.01");
        int next = routed.saveFormRecord(actor, values);
        assertThat(next).isGreaterThan(original).isNotEqualTo(2147483647);
        assertThat(forms.findForPatient(patient, original).getMeasurement(1).getWeight()).isEqualTo("4.51");
        assertThat(forms.findForPatient(patient, next).getMeasurement(1).getWeight()).isEqualTo("6.01");
        assertThat(forms.findForPatient(patient, next).getProviderNo()).isEqualTo(999998);
        assertThat(independentCount()).isEqualTo(2);
    }

    @Test
    void shouldRollBackEarlierFormWrites_whenALaterVersionIsRefused() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            int first = service.saveVersion(actor, patient, 0, values);
            assertThat(first).isPositive();
            service.saveVersion(actor, patient + 1, first, values);
        })).isInstanceOf(SecurityException.class);
        assertThat(independentCount()).isZero();
        assertThat(forms.findHistory(patient)).isEmpty();
    }

    @Test
    void shouldCommitRelatedVersions_whenTheOuterTransactionSucceeds() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            int first = service.saveVersion(actor, patient, 0, values);
            service.saveVersion(actor, patient, first, values);
        });
        assertThat(independentCount()).isEqualTo(2);
    }

    @Test
    void shouldRollBackAnEarlierInsert_whenARelatedDatabaseWriteFails() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.saveVersion(actor, patient, 0, values);
            entityManager.createNativeQuery("INSERT INTO formGrowth0_36 (demographic_no, formEdited) VALUES (NULL, CURRENT_TIMESTAMP)")
                    .executeUpdate();
        })).isInstanceOf(RuntimeException.class);
        assertThat(independentCount()).isZero();
    }

    @Test
    void shouldRollBackTheGeneratedInsert_whenTheServiceFailsAfterFlushing() throws Exception {
        Growth0To36FormService target = AopTestUtils.getUltimateTargetObject(service);
        FormGrowth0To36Dao failing = mock(FormGrowth0To36Dao.class, org.mockito.AdditionalAnswers.delegatesTo(forms));
        doAnswer(call -> {
            forms.flush();
            throw new IllegalStateException("synthetic failure after insert");
        }).when(failing).flush();
        try {
            ReflectionTestUtils.setField(target, "forms", failing);
            assertThatThrownBy(() -> service.saveVersion(actor, patient, 0, values)).isInstanceOf(IllegalStateException.class);
            assertThat(independentCount()).isZero();
        } finally {
            ReflectionTestUtils.setField(target, "forms", forms);
        }
    }

    @Test
    void shouldRefuseForeignOrMissingSources_withoutWritingOrExposingTheirValues() throws Exception {
        int id = service.saveVersion(actor, patient, 0, values);
        assertThatThrownBy(() -> service.load(actor, patient + 1, id)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, patient + 1, id, values)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.load(actor, patient, Integer.MAX_VALUE)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, patient, Integer.MAX_VALUE, values)).isInstanceOf(SecurityException.class);
        assertThat(independentCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnOnlyPatientVersions_forEncounterAndGroupedHistory() {
        int first = service.saveVersion(actor, patient, 0, values);
        int last = service.saveVersion(actor, patient, first, values);
        service.saveVersion(actor, patient + 1, 0, values);
        assertThat(forms.findHistory(patient)).extracting(FormGrowth0To36Dao.Version::id).containsExactly(last, first);
        assertThat(forms.findGroupedHistory(patient)).singleElement().satisfies(row -> assertThat(row.id()).isEqualTo(last));
        assertThat(EctFormData.getPatientForms(Integer.toString(patient), "formGrowth0_36"))
                .extracting(EctFormData.PatientForm::getFormId).containsExactly(Integer.toString(last), Integer.toString(first));
        assertThat(EctFormData.getGroupedPatientFormsAsArrayList(Integer.toString(patient), "Growth", "formGrowth0_36", "growth"))
                .singleElement().satisfies(row -> assertThat(row.getFormId()).isEqualTo(Integer.toString(last)));
    }

    @Test
    void shouldUseTheSelectedVersionsCreationDate_whenGroupingEditsByCalendarDay() throws Exception {
        int first = service.saveVersion(actor, patient, 0, values);
        values.setProperty("formCreated", "2025/01/02");
        int last = service.saveVersion(actor, patient, first, values);
        int earlierDay = service.saveVersion(actor, patient, last, values);
        try (var connection = source.getConnection(); var query = connection.prepareStatement(
                "UPDATE formGrowth0_36 SET formEdited = ? WHERE ID = ? AND demographic_no = ?")) {
            for (var edit : Map.of(first, "2026-03-04 23:45:00", last, "2026-03-04 01:15:00",
                    earlierDay, "2026-03-03 12:00:00").entrySet()) {
                query.setTimestamp(1, java.sql.Timestamp.valueOf(edit.getValue()));
                query.setInt(2, edit.getKey());
                query.setInt(3, patient);
                assertThat(query.executeUpdate()).isEqualTo(1);
            }
        }
        var history = forms.findGroupedHistory(patient);
        assertThat(history).extracting(FormGrowth0To36Dao.Version::id).containsExactly(earlierDay, last);
        assertThat(history.getLast().created()).isEqualTo(java.sql.Date.valueOf("2025-01-02"));
        assertThat(history.getLast().edited()).isEqualTo(java.sql.Timestamp.valueOf("2026-03-04 23:45:00"));
    }

    @Test
    void shouldArchiveCommittedRowsOnly_whenXmlInterchangeIsEnabled() throws Exception {
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        try {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            var transaction = new TransactionTemplate(transactions);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                service.saveVersion(actor, patient, 0, values);
                throw new IllegalStateException("synthetic failure after insert");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(independentCount()).isZero();
            try (var files = Files.list(archive)) { assertThat(files).isEmpty(); }
            int id = service.saveVersion(actor, patient, 0, values);
            try (var files = Files.list(archive)) {
                assertThat(files.toList()).singleElement().satisfies(file -> {
                    try {
                        String xml = Files.readString(file);
                        String idColumn = isH2() ? "id" : "ID";
                        assertThat(xml).contains("<Results>", "<Row>", "<" + idColumn + ">" + id + "</" + idColumn + ">",
                                "<date_20>2024-02-29</date_20>");
                    } catch (Exception e) { throw new AssertionError(e); }
                });
            }
        } finally {
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
        }
    }

    @Test
    void shouldPreserveTheXmlImportContract_whenArchivingCommittedValues() throws Exception {
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        try {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            values.setProperty("comment_20", "  Écho ' <&>  ");
            int id = service.saveVersion(actor, patient, 0, values);
            try (var files = Files.list(archive)) {
                var saved = files.toList();
                assertThat(saved).hasSize(1);
                Path file = saved.getFirst();
                var target = io.github.carlos_emr.carlos.util.JDBCUtil.parseImportFileName(file.getFileName().toString());
                assertThat(target.formName()).isEqualTo("formGrowth0_36");
                assertThat(target.demographicNo()).isEqualTo(Integer.toString(patient));
                assertThat(target.timeStamp()).matches("\\d{14}");
                var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(file.toFile());
                assertThat(xmlColumns(document)).isEqualTo(xmlColumns(forms.readArchive(patient, id)));
                assertThat(xmlColumns(document)).hasSize(131);
            }
        } finally {
            restore(configuration, "save_as_xml", previousEnabled);
            restore(configuration, "form_record_path", previousPath);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRetainCommittedFormAndLogSafely_whenArchiveReadOrWriteFails(boolean failRead) throws Exception {
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        Growth0To36FormService target = AopTestUtils.getUltimateTargetObject(service);
        FormGrowth0To36Dao archiveReader = mock(FormGrowth0To36Dao.class,
                org.mockito.AdditionalAnswers.delegatesTo(forms));
        if (failRead) {
            doAnswer(call -> {
                // The independent connection must see the form before the archive read.
                assertThat(independentCount()).isEqualTo(1);
                throw new IllegalStateException("synthetic sensitive archive failure: " + values.getProperty("patientName"));
            }).when(archiveReader).readArchive(eq(patient), anyInt());
        }
        // A regular file cannot be used as an archive directory, even when tests run as root.
        Path archivePath = failRead ? archive : Files.createFile(archive.resolve("not-a-directory"));
        // MiscUtils names the logger after the afterCommit callback's anonymous class.
        try (LogCapture logs = LogCapture.forLogger(Growth0To36FormService.class.getName() + "$1")) {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archivePath.toString());
            ReflectionTestUtils.setField(target, "forms", archiveReader);
            int id = service.saveVersion(actor, patient, 0, values);
            assertThat(id).isPositive();
            assertThat(independentCount()).isEqualTo(1);
            assertLegacyProperties(id, service.load(actor, patient, id));
            verify(archiveReader).readArchive(patient, id);
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .isEqualTo("Unable to archive committed Growth form XML; continuing without XML copy");
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

    private static void restore(Properties properties, String key, String value) {
        if (value == null) properties.remove(key); else properties.setProperty(key, value);
    }

    private void assertLegacyProperties(int id, Properties loaded) throws Exception {
        Properties legacy = new FrmRecordHelp().getFormRecord(
                "SELECT * FROM formGrowth0_36 WHERE demographic_no = ? AND ID = ?", patient, id);
        if (isH2()) {
            // The shared H2 harness folds unquoted identifiers to lowercase. MariaDB
            // uses the real baseline column spelling and must match the exact contract.
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

    private boolean isH2() throws Exception {
        try (var connection = source.getConnection()) {
            return "H2".equals(connection.getMetaData().getDatabaseProductName());
        }
    }

    private int independentCount() throws Exception {
        try (var connection = source.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM formGrowth0_36 WHERE demographic_no IN (?, ?)")) {
            query.setInt(1, patient);
            query.setInt(2, patient + 1);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }
}
