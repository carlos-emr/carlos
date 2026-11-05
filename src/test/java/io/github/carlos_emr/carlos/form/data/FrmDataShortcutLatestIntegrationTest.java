/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.data;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Executes FrmData's actual shortcut SELECT through LegacyJdbcQuery against isolated H2.
 * Only the form registry and thread-scoped Spring bean lookup are mocked; JDBC sorting,
 * patient filtering, parameter binding, and result handling use the real implementations.
 */
@Tag("integration")
@Tag("form")
@DisplayName("Latest form shortcut selection against a real isolated database")
class FrmDataShortcutLatestIntegrationTest {
    private static final String FORM_NAME = "Vascular Tracker";
    private static final String FORM_PATH = "../form/SetupForm.do?formName=VTForm&demographic_no=";
    private static final String EDITED = "2026-03-04 10:20:30";

    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private FrmData data;

    @BeforeEach
    void createOwnedDatabase() throws SQLException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:form_shortcut_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        // Closing the final connection destroys this test's database, including on failure.
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE formVTForm (ID INT PRIMARY KEY, demographic_no INT NOT NULL, formEdited TIMESTAMP NULL)");

        var registration = new EncounterForm();
        registration.setFormName(FORM_NAME);
        registration.setFormTable("formVTForm");
        registration.setFormValue(FORM_PATH);
        var forms = mock(EncounterFormDao.class);
        when(forms.findByFormName(FORM_NAME)).thenReturn(List.of(registration));
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
        data = new FrmData();
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldChooseHighestId_whenEditedTimestampsTieRegardlessOfInsertionOrder(boolean newestFirst) throws SQLException {
        insert(newestFirst ? 20 : 10, 101, EDITED);
        insert(newestFirst ? 10 : 20, 101, EDITED);

        assertShortcut("101", "20");
    }

    @Test
    void shouldPreferEditedTimestampOverId_whenHigherIdHasAnOlderEdit() throws SQLException {
        insert(900, 101, EDITED);
        insert(10, 101, "2026-03-04 10:20:31");

        assertShortcut("101", "10");
    }

    @Test
    void shouldKeepEachPatientLatestRevision_whenReadsAreInterleavedAndForeignIdsAreHigher() throws SQLException {
        insert(10, 101, EDITED);
        insert(20, 101, EDITED);
        insert(900, 202, "2030-01-02 12:00:00");
        insert(901, 202, "2030-01-02 12:00:00");

        assertShortcut("101", "20");
        assertShortcut("202", "901");
        assertShortcut("101", "20");
    }

    @Test
    void shouldChooseHighestOwnedId_whenAllEditedTimestampsAreNull() throws SQLException {
        insert(30, 101, null);
        insert(40, 101, null);
        insert(999, 202, null);

        assertShortcut("101", "40");
    }

    @Test
    void shouldPreferDatedRevision_whenHigherOwnedIdHasNoEditedTimestamp() throws SQLException {
        insert(900, 101, null);
        insert(10, 101, EDITED);

        assertShortcut("101", "10");
    }

    @Test
    void shouldOfferNewForm_whenOnlyAnotherPatientHasSavedRevisions() throws SQLException {
        insert(999, 202, "2030-01-02 12:00:00");

        assertShortcut("101", "0");
        assertShortcut("202", "999");
        assertShortcut("101", "0");
    }

    private void insert(int id, int patient, String edited) {
        jdbc.update("INSERT INTO formVTForm (ID, demographic_no, formEdited) VALUES (?, ?, ?)",
                id, patient, edited == null ? null : Timestamp.valueOf(edited));
    }

    private void assertShortcut(String patient, String id) throws SQLException {
        assertThat(data.getShortcutFormValue(patient, FORM_NAME)).containsExactly(FORM_PATH, id);
    }
}
