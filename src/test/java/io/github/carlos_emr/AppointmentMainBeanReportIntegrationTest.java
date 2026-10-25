/*
 * Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
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
import static org.mockito.Mockito.mockStatic;

/**
 * Executes the report index's shipped query definitions through the real legacy JDBC
 * boundary. The isolated database checks selection and binding together, rather than
 * accepting a mock that would also accept missing or misordered placeholders.
 *
 * @since 2026-10-25
 */
@Tag("integration")
@Tag("database")
@Tag("query")
class AppointmentMainBeanReportIntegrationTest {
    private final AppointmentMainBean bean = new AppointmentMainBean();
    private Connection keeper;
    private MockedStatic<SpringUtils> spring;
    private JdbcTemplate jdbc;

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

        // Keep the production query definitions authoritative; no copies of their SQL
        // or placeholder counts live in this fixture.
        List<String[]> queries = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("src/main/webapp/WEB-INF/jsp/report/reportMainBeanConn.jspf"))) {
            String definition = line.strip();
            if (!definition.startsWith("{\"")) continue;
            int keyEnd = definition.indexOf('"', 2);
            int sqlStart = definition.indexOf('"', keyEnd + 1) + 1;
            int sqlEnd = definition.indexOf('"', sqlStart);
            queries.add(new String[]{definition.substring(2, keyEnd), definition.substring(sqlStart, sqlEnd)});
        }
        assertThat(queries).extracting(definition -> definition[0]).contains(
                "search_provider", "site_search_provider", "team_search_provider",
                "search_group", "site_search_group", "search_waiting_list");
        bean.doConfigure(queries.toArray(String[][]::new));
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(source);
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
    void shouldMatchOnlyTheExactActiveWaitingList_whenGroupContainsSqlSyntax() throws Exception {
        assertThat(values("search_waiting_list", new String[]{"groupA"}, "ID")).containsExactly("1");
        assertThat(values("search_waiting_list", new String[]{"groupA' OR '1'='1"}, "ID")).containsExactly("4");
    }

    @Test
    void shouldReturnNoSiteRows_whenTheBoundProviderIsNull() throws Exception {
        assertThat(values("site_search_provider", new String[]{null}, "provider_no")).isEmpty();
    }

    private List<String> values(String operation, String[] parameters, String column) throws Exception {
        List<String> values = new ArrayList<>();
        try (ResultSet rows = bean.queryResults(parameters, operation)) {
            while (rows.next()) values.add(rows.getString(column));
        }
        return values;
    }
}
