/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Pins the legacy independent ID/edited maxima on the actual packaged SQL engine. */
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EncounterFormDaoGroupedHistoryIntegrationTest extends CarlosTestBase {
    @Autowired private DataSource dataSource;
    @Autowired private EncounterFormDao forms;
    @Autowired private PlatformTransactionManager transactions;
    private final String table = "formGrouped6093_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate jdbc;
    private EncounterForm registration;

    @BeforeEach
    void createOwnedPhysicalTable() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE " + table + " (ID INT PRIMARY KEY, demographic_no INT NOT NULL,"
                + " formCreated DATE, formEdited DATETIME)");
        registration = new EncounterForm();
        registration.setFormName(table);
        registration.setFormTable(table);
        registration.setFormValue("../form/SetupForm.do?demographic_no=");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> forms.persist(registration));
    }

    @AfterEach
    void removeOwnedPhysicalTable() {
        if (jdbc != null) jdbc.execute("DROP TABLE IF EXISTS " + table);
        if (registration != null && registration.getId() != null) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> forms.remove(registration.getId()));
        }
    }

    @Test
    void shouldGroupPhysicalHistoryWithoutCoveringOnlyXmlOrGrowthBranches() {
        // Creation date is constant within each day: the legacy SQL does not select
        // a deterministic creation date when it differs inside a group. Do not bless
        // an arbitrary engine-selected value or redefine this contract in a test.
        jdbc.update("INSERT INTO " + table + " VALUES (10,101,'2026-01-02','2026-03-10 16:00:00'),"
                + "(11,101,'2026-01-02','2026-03-10 08:00:00'),"
                + "(12,101,'2026-01-03','2026-03-11 10:00:00'),"
                + "(99,202,'2026-01-04','2026-03-10 23:00:00')");
        var grouped = forms.findPatientHistory(table, "101", true);
        assertThat(grouped).extracting(EncounterFormDao.HistoryRow::id).containsExactlyInAnyOrder(11, 12);
        assertThat(grouped).allMatch(row -> row.demographicNo() == 101);
        var firstDay = grouped.stream().filter(row -> row.id() == 11).findFirst().orElseThrow();
        assertThat(firstDay.edited().toString()).contains("2026-03-10 16:00:00");
        assertThat(firstDay.created().toString()).startsWith("2026-01-02");
        assertThat(forms.findPatientHistory(table, "101", false)).extracting(EncounterFormDao.HistoryRow::id)
                .containsExactly(12, 11, 10);
        assertThat(EctFormData.getGroupedPatientFormsAsArrayList("101", table, table,
                "../form/SetupForm.do?demographic_no=")).extracting(EctFormData.PatientForm::getFormId)
                .containsExactlyInAnyOrder("11", "12");
        assertThat(forms.findPatientHistory(table, "202", true)).extracting(EncounterFormDao.HistoryRow::id)
                .containsExactly(99);
    }
}
