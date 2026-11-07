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
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Executes report-index selections through the real typed native DAOs.
 * The isolated database checks selection and binding together, including provider
 * scope and admission of SQL structure.
 *
 * @since 2026-10-25
 */
@Tag("integration")
@Tag("database")
@Tag("query")
class AppointmentMainBeanReportIntegrationTest {
    private io.github.carlos_emr.carlos.test.base.NativeDaoTestDatabase nativeDatabase;
    private io.github.carlos_emr.carlos.commn.dao.ProviderDataDao providers;
    private io.github.carlos_emr.carlos.commn.dao.MyGroupDao groups;
    private Connection keeper;
    private MockedStatic<SpringUtils> spring;
    private JdbcTemplate jdbc;

    @Test
    void shouldRejectUnsafeDaySheetSort_beforePreparingNativeSql() {
        var appointments = nativeDatabase.dao(io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl.class);
        assertThatThrownBy(() -> appointments.findDaySheet("2026-01-01", "2026-01-02", "09:00", "18:00",
                null, false, "start_time; DELETE FROM appointment"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @BeforeEach
    void createOwnedDatabase() throws Exception {
        DataSource source = new DriverManagerDataSource("jdbc:h2:mem:report_index_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE provider (provider_no VARCHAR(60) PRIMARY KEY, first_name VARCHAR(30), "
                + "last_name VARCHAR(30), provider_type VARCHAR(20), status VARCHAR(1), team VARCHAR(30))");
        jdbc.execute("CREATE TABLE providersite (provider_no VARCHAR(60), site_id INT)");
        jdbc.execute("CREATE TABLE mygroup (mygroup_no VARCHAR(60), provider_no VARCHAR(60))");
        jdbc.execute("CREATE TABLE waitingListName (ID INT, group_no VARCHAR(60), is_history CHAR(1))");
        jdbc.update("INSERT INTO provider VALUES "
                + "('101','FAKE','Alpha','doctor','1','teamA'), "
                + "('102','FAKE','Beta','doctor','1','teamA'), "
                + "('201','FAKE','Gamma','doctor','1','teamB'), "
                + "('103','FAKE','Inactive','doctor','0','teamA'), "
                + "('104','FAKE','Nurse','nurse','1','teamB')");
        jdbc.update("INSERT INTO providersite VALUES ('101',1),('102',1),('103',1),('104',1),('201',2)");
        jdbc.update("INSERT INTO mygroup VALUES ('groupA','101'),('groupA','102'),('groupB','201')");
        jdbc.update("INSERT INTO waitingListName VALUES (1,'groupA','N'),(2,'groupA','Y'),(3,'groupB','N')");
        jdbc.update("INSERT INTO waitingListName VALUES (4,?,'N')", "groupA' OR '1'='1");

        nativeDatabase = new io.github.carlos_emr.carlos.test.base.NativeDaoTestDatabase(source);
        providers = nativeDatabase.dao(io.github.carlos_emr.carlos.commn.dao.ProviderDataDaoImpl.class);
        groups = nativeDatabase.dao(io.github.carlos_emr.carlos.commn.dao.MyGroupDaoImpl.class);
    }

    @AfterEach
    void closeOwnedDatabase() throws SQLException {
        if (nativeDatabase != null) nativeDatabase.close();
        if (keeper != null) keeper.close();
    }

    @Test
    void shouldListOnlyActiveDoctors_whenReportHasNoPrivacyFilter() throws Exception {
        assertThat(values("search_provider", new String[0], "provider_no")).containsExactly("101", "102", "201");
    }

    @Test
    void shouldLimitProvidersToTheSelectedSite_whenSitePrivacyIsEnabled() throws Exception {
        assertThat(values("site_search_provider", new String[]{"101"}, "provider_no"))
                .containsExactly("101", "102", "104");
    }

    @Test
    void shouldBindBothTeamParameters_whenTeamPrivacyIsEnabled() throws Exception {
        assertThat(values("team_search_provider", new String[]{"101", "101"}, "provider_no"))
                .containsExactly("101", "102");
    }

    @Test
    void shouldListDistinctGroups_whenReportHasNoSiteFilter() throws Exception {
        assertThat(values("search_group", new String[0], "mygroup_no")).containsExactly("groupA", "groupB");
    }

    @Test
    void shouldLimitGroupsToTheSelectedSite_whenSitePrivacyIsEnabled() throws Exception {
        assertThat(values("site_search_group", new String[]{"101"}, "mygroup_no")).containsExactly("groupA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"site_search_provider", "team_search_provider", "site_search_group"})
    void shouldTreatHostileProviderAsBoundData_withoutExpandingReportVisibility(String operation) throws Exception {
        String hostile = "101' OR '1'='1";
        String[] parameters = operation.equals("team_search_provider")
                ? new String[]{hostile, hostile} : new String[]{hostile};
        String column = operation.equals("site_search_group") ? "mygroup_no" : "provider_no";

        assertThat(values(operation, parameters, column)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider", Integer.class)).isEqualTo(5);
    }

    @Test
    void shouldReturnNoSiteRows_whenTheBoundProviderIsNull() throws Exception {
        assertThat(values("site_search_provider", new String[]{null}, "provider_no")).isEmpty();
    }

    private List<String> values(String operation, String[] parameters, String column) throws Exception {
        if (operation.endsWith("group")) {
            return groups.findReportGroups(parameters.length == 0 ? null : parameters[0], operation.startsWith("site_"));
        }
        var scope = operation.startsWith("site_") ? io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.ReportScope.SITE
                : operation.startsWith("team_") ? io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.ReportScope.TEAM
                : io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.ReportScope.ALL;
        return providers.findReportChoices(parameters.length == 0 ? null : parameters[0], scope).stream()
                .map(io.github.carlos_emr.carlos.commn.dao.ProviderDataDao.ReportChoice::providerNo).toList();
    }
}
