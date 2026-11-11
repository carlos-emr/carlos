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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Lists a patient's saved forms from rows that lack a created and/or edited time (issue #5994).
 *
 * <p>{@code formCreated} is nullable in every encounter form table and {@code formEdited} in several,
 * so real data can hold a row with neither. The real SQL of
 * {@link EctFormData#getPatientFormsFromLocalAndRemote(io.github.carlos_emr.carlos.utility.LoggedInInfo, String, String)}
 * runs against an isolated H2 database owned by each test; only the form registry and the
 * thread-scoped Spring bean lookup are mocked. The table is {@code formGrowth0_36}, a built-in
 * table {@code EctFormData} accepts without consulting its class-level registry DAO.</p>
 */
@Tag("integration")
@Tag("form")
@Tag("encounter")
@DisplayName("Patient form lists tolerate rows without created or edited times")
class EctFormDataUndatedRowsIntegrationTest {
    private static final String FORM_NAME = "Growth 0-36m";
    private static final String FORM_TABLE = "formGrowth0_36";
    private static final String FORM_PATH = "../form/formGrowth0_36.jsp?demographic_no=";
    private static final String PATIENT = "101";

    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach
    void createOwnedDatabase() throws SQLException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:form_undated_rows_" + UUID.randomUUID()
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

        insert(10, "2026-01-05", "2026-03-09 14:00:00");
        insert(20, "2026-02-10", null);
        insert(30, null, "2026-02-20 09:00:00");
        insert(40, null, null);
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
    @DisplayName("should list every row newest created first, undated last, when some rows have no created time")
    void shouldListAllRows_byCreatedWithUndatedLast() {
        var forms = new java.util.concurrent.atomic.AtomicReference<EctFormData.PatientForm[]>();

        assertThatCode(() -> forms.set(EctFormData.getPatientFormsFromLocalAndRemote(null, PATIENT, FORM_TABLE)))
                .doesNotThrowAnyException();

        // Effective time is created, else edited: 30 -> Feb 20, 20 -> Feb 10, 10 -> Jan 5, 40 -> none.
        assertThat(Arrays.stream(forms.get()).map(EctFormData.PatientForm::getFormId))
                .containsExactly("30", "20", "10", "40");
    }

    @Test
    @DisplayName("should list every row newest edited first, undated last, when some rows have no edited time")
    void shouldListAllRows_byEditedWithUndatedLast() {
        var forms = new java.util.concurrent.atomic.AtomicReference<EctFormData.PatientForm[]>();

        assertThatCode(() -> forms.set(
                EctFormData.getPatientFormsFromLocalAndRemote(null, PATIENT, FORM_TABLE, Boolean.TRUE)))
                .doesNotThrowAnyException();

        // Effective time is edited, else created: 10 -> Mar 9, 30 -> Feb 20, 20 -> Feb 10, 40 -> none.
        assertThat(Arrays.stream(forms.get()).map(EctFormData.PatientForm::getFormId))
                .containsExactly("10", "30", "20", "40");
    }

    private void insert(int id, String created, String edited) {
        jdbc.update("INSERT INTO " + FORM_TABLE + " (ID, demographic_no, formCreated, formEdited) VALUES (?, ?, ?, ?)",
                id, Integer.valueOf(PATIENT), created == null ? null : Date.valueOf(created),
                edited == null ? null : Timestamp.valueOf(edited));
    }
}
