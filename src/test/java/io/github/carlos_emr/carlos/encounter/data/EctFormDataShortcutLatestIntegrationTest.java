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
package io.github.carlos_emr.carlos.encounter.data;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.form.data.FrmData;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the contract behind the eChart Forms module item (issue #4678): the row the item is
 * dated from, {@link EctFormData#getShortcutLatestPatientForm(String, String)}, is the row its
 * {@code formId=latest} link opens, {@link FrmData#getShortcutFormValue(String, String)}.
 *
 * <p>Both real lookups run their real SQL through {@link LegacyJdbcQuery} against an isolated
 * H2 database owned by each test. Only the form registry and the thread-scoped Spring bean
 * lookup are mocked. The table is registered as {@code formGrowth0_36}, a built-in table that
 * {@code EctFormData} accepts without consulting its class-level registry DAO, so the result
 * does not depend on which test first loaded that class in a reused fork.</p>
 */
@Tag("integration")
@Tag("form")
@Tag("encounter")
@DisplayName("Forms module item row agrees with the latest form shortcut")
class EctFormDataShortcutLatestIntegrationTest {
    private static final String FORM_NAME = "Growth 0-36m";
    private static final String FORM_TABLE = "formGrowth0_36";
    private static final String FORM_PATH = "../form/formGrowth0_36.jsp?demographic_no=";
    private static final String PATIENT = "101";
    private static final String OTHER_PATIENT = "202";

    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach
    void createOwnedDatabase() throws SQLException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:form_item_latest_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        // Closing the final connection destroys this test's database, including on failure.
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE " + FORM_TABLE + " (ID INT PRIMARY KEY, demographic_no INT NOT NULL,"
                + " formCreated DATE NULL, formEdited TIMESTAMP NULL)");

        var registration = new EncounterForm();
        registration.setFormName(FORM_NAME);
        registration.setFormTable(FORM_TABLE);
        registration.setFormValue(FORM_PATH);
        var forms = mock(EncounterFormDao.class);
        when(forms.findByFormName(FORM_NAME)).thenReturn(List.of(registration));
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        try {
            LegacyJdbcQuery.releaseThreadResources();
        } finally {
            try {
                if (spring != null) spring.close();
            } finally {
                if (keeper != null) keeper.close();
            }
        }
    }

    @Test
    @DisplayName("should date the item from the row the shortcut opens when creation and edit order disagree")
    void shouldSelectShortcutRow_whenOlderCreatedRowWasEditedLater() throws SQLException {
        // Row 10 was created first but updated in place after row 20 was created.
        insert(10, PATIENT, "2026-01-05", "2026-03-09 14:00:00");
        insert(20, PATIENT, "2026-02-10", "2026-02-10 09:00:00");

        EctFormData.PatientForm selected = assertAgreesWithShortcut(PATIENT, "10");

        assertThat(selected.getEdited()).isEqualTo("09-03-2026 14:00:00");
        // The creation-ordered list the module used before #4678 leads with the other row.
        assertThat(EctFormData.getPatientFormsFromLocalAndRemote(null, PATIENT, FORM_TABLE)[0].getFormId())
                .isEqualTo("20");
    }

    @Test
    @DisplayName("should agree with the shortcut when the newest row is also the most recently edited")
    void shouldSelectNewestRow_whenRowsAreInsertedPerSave() throws SQLException {
        insert(10, PATIENT, "2026-01-05", "2026-01-05 08:00:00");
        insert(20, PATIENT, "2026-02-10", "2026-02-10 09:00:00");

        assertAgreesWithShortcut(PATIENT, "20");
    }

    @ParameterizedTest(name = "newest inserted first: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("should choose the highest ID when edit times tie, whatever the insertion order")
    void shouldSelectHighestId_whenEditTimesTie(boolean newestFirst) throws SQLException {
        insert(newestFirst ? 20 : 10, PATIENT, newestFirst ? "2026-01-05" : "2026-02-10", "2026-03-04 10:20:30");
        insert(newestFirst ? 10 : 20, PATIENT, newestFirst ? "2026-02-10" : "2026-01-05", "2026-03-04 10:20:30");

        assertAgreesWithShortcut(PATIENT, "20");
    }

    @Test
    @DisplayName("should compare sub-second edit precision the way the database does")
    void shouldSelectLaterFraction_whenEditsShareTheSameSecond() throws SQLException {
        insert(10, PATIENT, "2026-01-05", "2026-03-04 10:20:30.900");
        insert(20, PATIENT, "2026-02-10", "2026-03-04 10:20:30.100");

        assertAgreesWithShortcut(PATIENT, "10");
    }

    @Test
    @DisplayName("should prefer a dated row over a higher ID that has no edit time")
    void shouldSelectDatedRow_whenHigherIdHasNoEditTime() throws SQLException {
        insert(900, PATIENT, "2026-04-01", null);
        insert(10, PATIENT, "2026-01-05", "2026-03-04 10:20:30");

        assertAgreesWithShortcut(PATIENT, "10");
    }

    @Test
    @DisplayName("should choose the highest ID when no row has an edit time")
    void shouldSelectHighestId_whenNoRowHasAnEditTime() throws SQLException {
        insert(30, PATIENT, "2026-04-01", null);
        insert(40, PATIENT, "2026-01-05", null);

        assertAgreesWithShortcut(PATIENT, "40");
    }

    @Test
    @DisplayName("should ignore another patient's later and higher-numbered rows")
    void shouldSelectOwnRow_whenAnotherPatientHasLaterRows() throws SQLException {
        insert(10, PATIENT, "2026-01-05", "2026-03-04 10:20:30");
        insert(20, PATIENT, "2026-02-10", "2026-02-10 09:00:00");
        insert(900, OTHER_PATIENT, "2030-01-02", "2030-01-02 12:00:00");

        EctFormData.PatientForm selected = assertAgreesWithShortcut(PATIENT, "10");
        assertThat(selected.getDemoNo()).isEqualTo(PATIENT);
        assertAgreesWithShortcut(OTHER_PATIENT, "900");
    }

    @Test
    @DisplayName("should report no started form when only another patient has rows")
    void shouldReturnNull_whenPatientHasNoRows() throws SQLException {
        insert(900, OTHER_PATIENT, "2030-01-02", "2030-01-02 12:00:00");

        assertThat(EctFormData.getShortcutLatestPatientForm(PATIENT, FORM_TABLE)).isNull();
        assertThat(new FrmData().getShortcutFormValue(PATIENT, FORM_NAME)).containsExactly(FORM_PATH, "0");
    }

    private EctFormData.PatientForm assertAgreesWithShortcut(String patient, String expectedId) throws SQLException {
        String[] shortcut = new FrmData().getShortcutFormValue(patient, FORM_NAME);
        EctFormData.PatientForm selected = EctFormData.getShortcutLatestPatientForm(patient, FORM_TABLE);

        assertThat(shortcut).containsExactly(FORM_PATH, expectedId);
        assertThat(selected).isNotNull();
        assertThat(selected.getFormId()).isEqualTo(expectedId);
        return selected;
    }

    private void insert(int id, String patient, String created, String edited) {
        jdbc.update("INSERT INTO " + FORM_TABLE + " (ID, demographic_no, formCreated, formEdited) VALUES (?, ?, ?, ?)",
                id, Integer.valueOf(patient), created == null ? null : Date.valueOf(created),
                edited == null ? null : Timestamp.valueOf(edited));
    }
}
