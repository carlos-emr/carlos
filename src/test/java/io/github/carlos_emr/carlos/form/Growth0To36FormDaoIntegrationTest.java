/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.forms.Growth0To36FormDao;
import io.github.carlos_emr.carlos.form.model.Growth0To36Form;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real commits and rollbacks, also run against the physical ON and BC Flyway schemas. */
@Tag("integration")
@Tag("dao")
@Tag("form")
@ContextConfiguration(locations = "classpath:test-context-growth036.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Growth0To36FormDaoIntegrationTest extends CarlosTestBase {
    private static final int PATIENT = 569301;
    @Autowired private Growth0To36FormService service;
    @Autowired private Growth0To36FormDao forms;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DataSource dataSource;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @TempDir Path archives;
    private LoggedInInfo actor;
    private TransactionTemplate tx;
    private Growth0To36FormService target;
    private String originalXml;
    private String originalPath;

    @BeforeEach
    void prepareActorAndArchiveSettings() {
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        tx = new TransactionTemplate(transactions);
        target = AopTestUtils.getTargetObject(service);
        originalXml = CarlosProperties.getInstance().getProperty("save_as_xml");
        originalPath = CarlosProperties.getInstance().getProperty("form_record_path");
        CarlosProperties.getInstance().setProperty("save_as_xml", "false");
        CarlosProperties.getInstance().setProperty("form_record_path", archives.toString());
    }

    @AfterEach
    void removeCommittedFixtures() throws Exception {
        restore("save_as_xml", originalXml);
        restore("form_record_path", originalPath);
        ReflectionTestUtils.setField(target, "forms", forms);
        tx.executeWithoutResult(status -> em.createQuery("DELETE FROM Growth0To36Form WHERE demographicNo IN (:patients)")
                .setParameter("patients", java.util.List.of(PATIENT, PATIENT + 1)).executeUpdate());
    }

    private static void restore(String key, String previous) {
        if (previous == null) CarlosProperties.getInstance().remove(key);
        else CarlosProperties.getInstance().setProperty(key, previous);
    }

    private Properties values() {
        Properties values = new Properties();
        values.setProperty("demographic_no", Integer.toString(PATIENT));
        values.setProperty("provider_no", "111111");
        values.setProperty("ID", "2147483647");
        values.setProperty("formCreated", "2026/10/14");
        values.setProperty("formEdited", "2000/01/01");
        values.setProperty("patientName", "  Écho ' OR 1=1 --  ");
        values.setProperty("recordNo", "GC-5693");
        values.setProperty("motherStature", "165");
        values.setProperty("fatherStature", "185");
        values.setProperty("gestationalAge", "39+2");
        values.setProperty("edc", "2025/01/01");
        for (int i = 1; i <= 20; i++) {
            values.setProperty("date_" + i, "2026" + (i % 2 == 0 ? "-10-" : "/10/") + String.format("%02d", i));
            values.setProperty("age_" + i, Integer.toString(i));
            values.setProperty("weight_" + i, i + ".25");
            values.setProperty("length_" + i, "75." + i);
            values.setProperty("headCirc_" + i, "45." + i);
            values.setProperty("comment_" + i, "Écho visit " + i);
        }
        return values;
    }

    private int committedCount() throws Exception {
        // Deliberately bypass Spring's connection binding and both persistence contexts.
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM formGrowth0_36 WHERE demographic_no=?")) {
            statement.setInt(1, PATIENT);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }

    @Test
    void shouldCommitEveryPhysicalColumnAndGenerateFreshIds_whenSavingVersions() throws Exception {
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        Properties submitted = values();
        int first = service.saveVersion(actor, submitted);
        int second = service.saveVersion(actor, submitted);
        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first).isNotEqualTo(2147483647);
        assertThat(committedCount()).isEqualTo(2);
        Properties loaded = service.load(actor, PATIENT, second);
        for (String name : submitted.stringPropertyNames()) {
            if (java.util.Set.of("ID", "provider_no", "formEdited").contains(name)) continue;
            String expected = submitted.getProperty(name).trim();
            if (name.startsWith("date_")) expected = expected.replace('-', '/');
            assertThat(loaded.getProperty(name)).as(name).isEqualTo(expected);
        }
        assertThat(loaded.getProperty("ID")).isEqualTo(Integer.toString(second));
        assertThat(loaded.getProperty("provider_no")).isEqualTo("999998");
        assertThat(loaded.getProperty("formEdited")).matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");
        try (var connection = dataSource.getConnection(); var columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, connection.getMetaData().storesLowerCaseIdentifiers()
                        ? "formgrowth0_36" : "formGrowth0_36", null)) {
            int count = 0;
            while (columns.next()) count++;
            assertThat(count).isEqualTo(131);
        }
        assertThat(forms.findVersions(PATIENT)).extracting(Growth0To36FormDao.Version::id).containsExactly(second, first);
        assertThat(forms.findGroupedVersions(PATIENT)).singleElement().satisfies(row -> assertThat(row.id()).isEqualTo(second));
        assertThat(forms.findVersions(PATIENT + 1)).isEmpty();
        assertThat(forms.findForPatient(PATIENT + 1, second)).isNull();
        assertThatThrownBy(() -> service.load(actor, PATIENT + 1, second)).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldPreserveSqlNullAndBlankDisplayValues_whenOptionalFieldsAreAbsent() throws Exception {
        Properties values = new Properties();
        values.setProperty("demographic_no", Integer.toString(PATIENT));
        values.setProperty("date_1", "invalid");
        int id = service.saveVersion(actor, values);
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT patientName, formCreated, date_1, weight_20 FROM formGrowth0_36 WHERE ID=? AND demographic_no=?")) {
            query.setInt(1, id);
            query.setInt(2, PATIENT);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                for (int i = 1; i <= 4; i++) assertThat(rows.getObject(i)).isNull();
            }
        }
        Properties loaded = service.load(actor, PATIENT, id);
        assertThat(loaded).containsEntry("patientName", "").containsEntry("formCreated", "")
                .containsEntry("date_1", "").containsEntry("weight_20", "");
    }

    @Test
    void shouldRollBackAnEarlierFormInsert_whenARelatedWriteFails() throws Exception {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.saveVersion(actor, values());
            // The second real database write fails its NOT NULL patient constraint.
            em.createNativeQuery("INSERT INTO formGrowth0_36 (demographic_no, formEdited) VALUES (NULL, CURRENT_TIMESTAMP)")
                    .executeUpdate();
        })).isInstanceOf(RuntimeException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void shouldRollBackTheGeneratedInsert_whenTheServiceFailsAfterFlushing() throws Exception {
        Growth0To36FormDao failing = mock(Growth0To36FormDao.class, org.mockito.AdditionalAnswers.delegatesTo(forms));
        doAnswer(call -> { forms.flush(); throw new IllegalStateException("synthetic failure after form insert"); })
                .when(failing).flush();
        ReflectionTestUtils.setField(target, "forms", failing);
        assertThatThrownBy(() -> service.saveVersion(actor, values())).isInstanceOf(IllegalStateException.class);
        assertThat(committedCount()).isZero();
    }

    @Test
    void shouldArchiveOnlyCommittedRows_whenXmlCopiesAreEnabled() throws Exception {
        CarlosProperties.getInstance().setProperty("save_as_xml", "true");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.saveVersion(actor, values());
            throw new IllegalStateException("synthetic later write failure");
        })).isInstanceOf(IllegalStateException.class);
        try (var files = Files.list(archives)) { assertThat(files).isEmpty(); }
        int id = service.saveVersion(actor, values());
        try (var files = Files.list(archives)) {
            var saved = files.toList();
            assertThat(saved).hasSize(1);
            String xml = Files.readString(saved.getFirst());
            assertThat(xml).contains("<Results>", "<Row>", "<ID>" + id + "</ID>",
                    "<date_20>2026-10-20</date_20>", "<comment_20>Écho visit 20</comment_20>");
        }
        assertThat(committedCount()).isEqualTo(1);
    }
}
