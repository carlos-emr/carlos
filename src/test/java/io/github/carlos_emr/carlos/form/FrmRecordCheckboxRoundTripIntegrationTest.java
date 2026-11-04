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
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.sql.DataSource;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Save, reopen and save again on the real table definitions of the legacy questionnaires whose record
 * classes once compared the column type with {@code TINY} (issue #4466). A box ticked and saved must be
 * ticked when the form is reopened, and must still be ticked after the page the clinician sees is saved
 * again; before the fix the reopened page showed every box unticked, so the second save filed a version
 * with the ticks gone.
 *
 * <p>The tables are the {@code CREATE TABLE} statements of the Flyway baseline, loaded into an isolated
 * H2 database (MySQL mode) that this test owns, so the column list of each form is the shipped one. H2
 * reports {@code tinyint(1)} as {@code TINYINT}; MySQL Connector/J reports it as {@code BIT}, which
 * {@link FrmRecordCheckboxRestoreUnitTest} covers. Grip Strength and Self Efficacy are left out here
 * because their tables hold no checkbox columns.
 *
 * @since 2026-10-19
 */
@DisplayName("Legacy form checkbox save and reopen on the baseline tables")
@Tag("integration")
@Tag("form")
class FrmRecordCheckboxRoundTripIntegrationTest {

    private static final int PATIENT = 101;
    private static final String CHECKED = "checked='checked'";
    private static final Path BASELINE = Path.of("database/mysql/migration/common/V1__baseline_schema.sql");

    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<CarlosProperties> properties;

    @BeforeEach
    void prepareOwnedDatabase() throws SQLException, IOException {
        var source = new DriverManagerDataSource("jdbc:h2:mem:frm_checkbox_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DEFAULT_NULL_ORDERING=HIGH", "sa", "");
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        loadBaselineTables(tableNames());

        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
        // FrmRecord's constructor looks both up; saving and reopening a form uses neither.
        var demographicManager = mock(DemographicManager.class);
        var demographicExtDao = mock(DemographicExtDao.class);
        spring.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(demographicManager);
        spring.when(() -> SpringUtils.getBean(DemographicExtDao.class)).thenReturn(demographicExtDao);
        var config = mock(CarlosProperties.class);
        when(config.getProperty("db_type", "")).thenReturn("mysql");
        when(config.getProperty("save_as_xml", "false")).thenReturn("false");
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        if (properties != null) properties.close();
        if (spring != null) spring.close();
        if (keeper != null) keeper.close(); // The last connection closing destroys this test's database.
    }

    /** The tables the parameterized cases below read and write. */
    private static List<String> tableNames() {
        return roundTripCases().map(args -> (String) args.get()[1]).toList();
    }

    private static Stream<Arguments> roundTripCases() {
        record Case(String name, String table, Supplier<FrmRecord> factory) {
        }
        return Stream.of(
                new Case("Frm2MinWalkRecord", "form2MinWalk", Frm2MinWalkRecord::new),
                new Case("FrmCESDRecord", "formCESD", FrmCESDRecord::new),
                new Case("FrmCaregiverRecord", "formCaregiver", FrmCaregiverRecord::new),
                new Case("FrmCostQuestionnaireRecord", "formCostQuestionnaire",
                        FrmCostQuestionnaireRecord::new),
                new Case("FrmFallsRecord", "formFalls", FrmFallsRecord::new),
                new Case("FrmHomeFallsRecord", "formHomeFalls", FrmHomeFallsRecord::new),
                new Case("FrmIntakeInfoRecord", "formIntakeInfo", FrmIntakeInfoRecord::new),
                new Case("FrmInternetAccessRecord", "formInternetAccess",
                        FrmInternetAccessRecord::new),
                new Case("FrmLateLifeFDIDisabilityRecord", "formLateLifeFDIDisability",
                        FrmLateLifeFDIDisabilityRecord::new),
                new Case("FrmLateLifeFDIFunctionRecord", "formLateLifeFDIFunction",
                        FrmLateLifeFDIFunctionRecord::new),
                new Case("FrmRhImmuneGlobulinRecord", "formRhImmuneGlobulin",
                        FrmRhImmuneGlobulinRecord::new),
                new Case("FrmSF36CaregiverRecord", "formSF36Caregiver", FrmSF36CaregiverRecord::new),
                new Case("FrmSF36Record", "formSF36", FrmSF36Record::new),
                new Case("FrmSatisfactionScaleRecord", "formSatisfactionScale",
                        FrmSatisfactionScaleRecord::new),
                new Case("FrmSelfAdministeredRecord", "formSelfAdministered",
                        FrmSelfAdministeredRecord::new),
                new Case("FrmSelfManagementRecord", "formSelfManagement", FrmSelfManagementRecord::new),
                new Case("FrmTreatmentPrefRecord", "formTreatmentPref", FrmTreatmentPrefRecord::new))
                .map(c -> Arguments.of(c.name(), c.table(), c.factory()));
    }

    /**
     * Creates each named table from its {@code CREATE TABLE} statement in the baseline schema. A statement runs from
     * its opening line to the first line that starts with the closing parenthesis; the trailing {@code ENGINE=...}
     * options are MySQL-only and are dropped.
     */
    private void loadBaselineTables(List<String> tables) throws IOException {
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8);
        for (String table : tables) {
            String opening = "CREATE TABLE `" + table + "` (";
            int start = baseline.indexOf(opening);
            assertThat(start).as("CREATE TABLE of " + table + " in the baseline schema").isGreaterThanOrEqualTo(0);
            int end = baseline.indexOf("\n)", start);
            assertThat(end).as("end of the CREATE TABLE of " + table).isGreaterThan(start);
            jdbc.execute(baseline.substring(start, end + 2));
        }
    }

    /**
     * Saves the post as a new version and returns its id. The record classes insert through an updatable result set
     * with {@code ID} set to 0, which MySQL turns into the next generated id; H2 stores the 0 itself, so the new
     * row is numbered here the way MySQL would have numbered it.
     */
    private int save(FrmRecord formRecord, String table, Properties post) throws SQLException {
        formRecord.saveFormRecord(post);
        Integer last = jdbc.queryForObject("SELECT COALESCE(MAX(ID), 0) FROM `" + table + "`", Integer.class);
        int id = last + 1;
        assertThat(jdbc.update("UPDATE `" + table + "` SET ID = ? WHERE ID = 0", id))
                .as("rows the save inserted as ID 0").isEqualTo(1);
        return id;
    }

    /** The hidden inputs every one of these forms posts: the patient and the study id ("N/A" outside a study). */
    private static Properties browserPost() {
        Properties post = new Properties();
        post.setProperty("demographic_no", String.valueOf(PATIENT));
        post.setProperty("studyID", "N/A");
        return post;
    }

    /** The columns H2 reports as TINYINT: the checkbox answers of the form. */
    private List<String> checkboxColumns(String table) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? "
                + "AND DATA_TYPE = 'TINYINT' ORDER BY ORDINAL_POSITION", String.class, table);
    }

    @ParameterizedTest(name = "{0} shows saved ticks again")
    @MethodSource("roundTripCases")
    void shouldShowSavedTicksAsChecked_whenFormIsReopened(String recordName, String table,
            Supplier<FrmRecord> factory) throws Exception {
        List<String> boxes = checkboxColumns(table);
        assertThat(boxes).as("checkbox columns of " + table).hasSizeGreaterThanOrEqualTo(2);
        String ticked = boxes.get(0);
        String unticked = boxes.get(1);
        FrmRecord formRecord = factory.get();

        Properties posted = browserPost();
        posted.setProperty(ticked, "on"); // what a browser posts for a ticked box; an unticked one posts nothing
        int formId = save(formRecord, table, posted);

        assertThat(jdbc.queryForObject("SELECT `" + ticked + "` FROM `" + table + "` WHERE ID = ?", Integer.class,
                formId)).as("stored value of the ticked box").isEqualTo(1);

        Properties reopened = formRecord.getFormRecord(null, PATIENT, formId);

        assertThat(reopened.getProperty(ticked)).as("ticked box " + ticked).isEqualTo(CHECKED);
        assertThat(reopened.getProperty(unticked)).as("unticked box " + unticked).isEmpty();
    }

    @ParameterizedTest(name = "{0} keeps its ticks when the reopened page is saved again")
    @MethodSource("roundTripCases")
    void shouldKeepTicks_whenReopenedPageIsSavedAgain(String recordName, String table, Supplier<FrmRecord> factory)
            throws Exception {
        List<String> boxes = checkboxColumns(table);
        String ticked = boxes.get(0);
        FrmRecord formRecord = factory.get();

        Properties firstPost = browserPost();
        firstPost.setProperty(ticked, "on");
        int firstId = save(formRecord, table, firstPost);

        // The reopened page: a box shown ticked is posted back as "on", one shown blank is not posted.
        Properties reopened = formRecord.getFormRecord(null, PATIENT, firstId);
        Properties secondPost = browserPost();
        for (String box : boxes) {
            if (CHECKED.equals(reopened.getProperty(box))) {
                secondPost.setProperty(box, "on");
            }
        }
        int secondId = save(formRecord, table, secondPost);

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(jdbc.queryForObject("SELECT `" + ticked + "` FROM `" + table + "` WHERE ID = ?", Integer.class,
                secondId)).as("the second version still holds the tick").isEqualTo(1);
    }
}
