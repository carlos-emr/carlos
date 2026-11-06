/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.forms.FormGrowth0To36Dao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
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
    @Autowired private PlatformTransactionManager transactions;
    @Autowired @Qualifier("dataSource") private DataSource source;
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
            values.setProperty("age_" + slot, "2m");
            values.setProperty("weight_" + slot, "4.51");
            values.setProperty("length_" + slot, "53.2");
            values.setProperty("headCirc_" + slot, "35.2");
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
    void shouldRefuseForeignOrMissingSources_withoutWritingOrExposingTheirValues() throws Exception {
        int id = service.saveVersion(actor, patient, 0, values);
        assertThatThrownBy(() -> service.load(actor, patient + 1, id)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, patient + 1, id, values)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.load(actor, patient, Integer.MAX_VALUE)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.saveVersion(actor, patient, Integer.MAX_VALUE, values)).isInstanceOf(SecurityException.class);
        assertThat(independentCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnOnlyPatientVersions_forEncounterAndGroupedHistory() throws Exception {
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
    void shouldArchiveCommittedRowsOnly_whenXmlInterchangeIsEnabled() throws Exception {
        Properties configuration = CarlosProperties.getInstance();
        String previousEnabled = configuration.getProperty("save_as_xml");
        String previousPath = configuration.getProperty("form_record_path");
        try {
            configuration.setProperty("save_as_xml", "true");
            configuration.setProperty("form_record_path", archive.toString());
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
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
