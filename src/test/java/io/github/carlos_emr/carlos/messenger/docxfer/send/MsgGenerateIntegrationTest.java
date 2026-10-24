/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.messenger.docxfer.send;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Exercises the packaged transfer definition through real JDBC, including patient and row filters.
 *
 * @since 2026-10-24
 */
@Tag("integration")
@Tag("messenger")
public class MsgGenerateIntegrationTest {
    private Connection keeper;
    private JdbcTemplate jdbc;
    private MockedStatic<SpringUtils> spring;
    private DataSource source;

    @BeforeEach
    void createOwnedDatabase() throws SQLException {
        source = new DriverManagerDataSource("jdbc:h2:mem:docxfer_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=REPEAT", "sa", "");
        keeper = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE ALIAS ELT FOR '" + getClass().getName() + ".elt'");
        jdbc.execute("CREATE TABLE provider (provider_no VARCHAR(6), last_name VARCHAR(80), first_name VARCHAR(80))");
        jdbc.execute("CREATE TABLE demographic (demographic_no INT, provider_no VARCHAR(6), last_name VARCHAR(80), "
                + "first_name VARCHAR(80), address VARCHAR(80), city VARCHAR(80), province VARCHAR(2), postal VARCHAR(10), "
                + "phone VARCHAR(30), phone2 VARCHAR(30), year_of_birth VARCHAR(4), month_of_birth VARCHAR(2), "
                + "date_of_birth VARCHAR(2), hin VARCHAR(20))");
        jdbc.execute("CREATE TABLE drugs (demographic_no INT, provider_no VARCHAR(6), archived INT, rx_date DATE, "
                + "BN VARCHAR(80), takemin INT, takemax INT, freqcode VARCHAR(10), duration VARCHAR(10), "
                + "durunit VARCHAR(10), quantity VARCHAR(10), repeat INT, nosubs INT, prn INT, special VARCHAR(200))");
        jdbc.execute("CREATE TABLE allergies (demographic_no INT, DESCRIPTION VARCHAR(80), entry_date DATE, TYPECODE INT)");
        jdbc.execute("CREATE TABLE eChart (eChartId INT, demographicNo INT, encounter VARCHAR(200), "
                + "providerNo VARCHAR(6), timeStamp TIMESTAMP)");
        jdbc.update("INSERT INTO provider VALUES ('101', 'FAKE clinician', 'A')");
        jdbc.update("INSERT INTO demographic (demographic_no, provider_no, last_name, first_name) "
                + "VALUES (101, '101', 'FAKE selected', 'Patient'), (202, '101', 'FAKE other private', 'Patient')");
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
    void shouldExportOnlySelectedClinicalRows_whenPackagedDefinitionQueriesRealDatabase() throws Exception {
        String instruction = "FAKE <tag> & 'quoted' directions";
        jdbc.update("INSERT INTO drugs (demographic_no, provider_no, archived, rx_date, BN, special) "
                + "VALUES (101, '101', 0, '2026-01-01', 'FAKE active', ?)", instruction);
        jdbc.update("INSERT INTO drugs (demographic_no, archived, BN) "
                + "VALUES (101, 1, 'FAKE archived'), (202, 0, 'FAKE other private')");
        jdbc.update("INSERT INTO allergies VALUES (101, 'FAKE allergy', '2026-01-01', 1), "
                + "(202, 'FAKE other private', '2026-01-01', 1)");
        jdbc.update("INSERT INTO eChart VALUES (1,101,'FAKE old encounter','101','2026-01-01 12:00:00'), "
                + "(2,101,'FAKE latest encounter','101','2026-01-02 12:00:00'), "
                + "(3,202,'FAKE other private','101','2026-01-03 12:00:00')");

        Document document = new MsgGenerate().getDocument(101);

        assertThat(document.getElementsByTagName("table").getLength()).isEqualTo(4);
        assertThat(value(document, "//table[@name='Prescription Info']/item/content/fld[@name='Special Instructions']/@value"))
                .isEqualTo(instruction);
        assertThat(value(document, "//data/*[name()='drugs.special']/text()")).isEqualTo(instruction);
        assertThat(value(document, "//table[@name='Encounter Info']/item/content/fld[@name='Encounter']/@value"))
                .isEqualTo("FAKE latest encounter");
        assertThat(value(document, "count(//table[@name='Prescription Info']/item)")).isEqualTo("1");
        assertThat(value(document, "count(//table[@name='Drug Allergies']/item)")).isEqualTo("1");
        assertThat(value(document, "count(//@value[contains(.,'other private') or contains(.,'archived')])"))
                .isEqualTo("0");
        assertThat(value(document, "//item[@itemId='0']/@name")).isEqualTo("Patient");
        assertThat(value(document, "//item[@itemId='3']/@name")).isEqualTo("Encounter");
    }

    @Test
    void shouldOmitEmptyClinicalSections_whenPatientHasOnlyDemographics() throws Exception {
        var generator = new MsgGenerate();
        generator.getDocument(101);
        Document document = generator.getDocument(202);

        assertThat(document.getElementsByTagName("table").getLength()).isEqualTo(1);
        assertThat(value(document, "//item/@itemId")).isEqualTo("0");
        assertThat(value(document, "//fld[@name='Name']/@value")).isEqualTo("FAKE other private, Patient");
    }

    @Test
    void shouldPropagateDatabaseFailure_whenClinicalSectionCannotBeRead() {
        jdbc.execute("DROP TABLE allergies");

        assertThatThrownBy(() -> new MsgGenerate().getDocument(101)).isInstanceOf(SQLException.class);
    }

    @Test
    void shouldPropagateColumnReadFailure_whenDatabaseFailsAfterQueryExecution() {
        Map<Class<?>, Integer> closed = new HashMap<>();
        DataSource failing = new DelegatingDataSource(source) {
            @Override
            public Connection getConnection() throws SQLException {
                return failOnColumnRead(Connection.class, source.getConnection(), closed);
            }
        };
        spring.when(() -> SpringUtils.getBean(DataSource.class)).thenReturn(failing);

        assertThatThrownBy(() -> new MsgGenerate().getDocument(101))
                .isInstanceOf(SQLException.class).hasMessage("FAKE column read failure");
        assertThat(closed).containsEntry(ResultSet.class, 1).containsEntry(PreparedStatement.class, 1)
                .containsEntry(Connection.class, 1);
    }

    /**
     * H2 equivalent of MariaDB's ELT used by the actual bundled transfer definition.
     *
     * @param index Integer one-based choice, or null
     * @param values String[] the supplied choices
     * @return String selected choice, or null for an absent or out-of-range index
     */
    public static String elt(Integer index, String... values) {
        return index == null || index < 1 || index > values.length ? null : values[index - 1];
    }

    private static String value(Document document, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, document);
    }

    /** Injects one JDBC read failure while leaving queries, metadata and resource cleanup real. */
    private static <T> T failOnColumnRead(Class<T> type, T delegate, Map<Class<?>, Integer> closed) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("close")) closed.merge(type, 1, Integer::sum);
            if (type == ResultSet.class && method.getName().equals("getString") && args[0] instanceof Integer) {
                throw new SQLException("FAKE column read failure");
            }
            try {
                Object result = method.invoke(delegate, args);
                if (result instanceof PreparedStatement statement && method.getName().equals("prepareStatement")) {
                    return failOnColumnRead(PreparedStatement.class, statement, closed);
                }
                if (result instanceof ResultSet rows && method.getName().equals("executeQuery")) {
                    return failOnColumnRead(ResultSet.class, rows, closed);
                }
                return result;
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }));
    }
}
