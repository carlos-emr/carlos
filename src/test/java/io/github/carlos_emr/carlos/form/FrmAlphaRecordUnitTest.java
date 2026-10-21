/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("form")
class FrmAlphaRecordUnitTest {
    private MockedStatic<SpringUtils> spring;
    private MockedStatic<LegacyJdbcQuery> jdbc;
    private FrmAlphaRecord record;

    @BeforeEach
    void setUp() {
        spring = mockStatic(SpringUtils.class);
        jdbc = mockStatic(LegacyJdbcQuery.class);
        record = new FrmAlphaRecord();
    }

    @AfterEach
    void tearDown() {
        jdbc.close();
        spring.close();
    }

    @Test
    void shouldOpenANewFormWithTheRequestedPatientNameAndCreationDate() throws Exception {
        ResultSet demographic = mock(ResultSet.class);
        when(demographic.next()).thenReturn(true);
        when(demographic.getString("demographic_no")).thenReturn("123");
        when(demographic.getString("pName")).thenReturn("FAKE, Patient");
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(
                "SELECT demographic_no, CONCAT(last_name, ', ', first_name) AS pName FROM demographic WHERE demographic_no = ?", 123))
                .thenReturn(demographic);
        Properties props = record.getFormRecord(null, 123, 0);
        assertThat(props).containsEntry("demographic_no", "123").containsEntry("pName", "FAKE, Patient");
        assertThat(props.getProperty("formCreated")).matches("\\d{4}/\\d{2}/\\d{2}");
        verify(demographic).close();
    }

    @Test
    void shouldReopenEveryPersistedColumnIncludingTextDatesAndCheckboxes() throws Exception {
        Map<String, String> schema = schema();
        Properties values = values(schema);
        ResultSet saved = savedRow(schema, values);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet("SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?", 123, 99)).thenReturn(saved);
        Properties reopened = record.getFormRecord(null, 123, 99);
        assertThat(reopened).hasSize(schema.size());
        for (var col : schema.entrySet()) {
            String expected = col.getValue().equals("BIT") ? (values.getProperty(col.getKey()).equals("1") ? "checked='checked'" : "") : values.getProperty(col.getKey());
            assertThat(reopened.getProperty(col.getKey())).as(col.getKey()).isEqualTo(expected);
        }
        verify(saved).close();
    }

    @Test
    void shouldNotReopenARecordWhenTheRequestedPatientDoesNotOwnIt() throws Exception {
        ResultSet none = mock(ResultSet.class);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet("SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?", 124, 99)).thenReturn(none);
        assertThat(record.getFormRecord(null, 124, 99)).isEmpty();
        verify(none).close();
    }

    @Test
    void shouldPrintEveryPersistedColumnWithoutLosingClinicalTextOrCheckboxAnswers() throws Exception {
        Map<String, String> schema = schema();
        Properties values = values(schema);
        ResultSet saved = savedRow(schema, values);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet("SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?", 123, 99)).thenReturn(saved);
        Properties printed = record.getPrintRecord(123, 99);
        assertThat(printed).hasSize(schema.size());
        for (var col : schema.entrySet()) {
            String expected = col.getValue().equals("BIT") ? (values.getProperty(col.getKey()).equals("1") ? "on" : "off") : values.getProperty(col.getKey());
            assertThat(printed.getProperty(col.getKey())).as(col.getKey()).isEqualTo(expected);
        }
    }

    @Test
    void shouldNotPrintARecordForAnotherPatient() throws Exception {
        ResultSet none = mock(ResultSet.class);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet("SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?", 124, 99)).thenReturn(none);
        assertThat(record.getPrintRecord(124, 99)).isEmpty();
    }

    @Test
    void shouldSaveEverySchemaColumnThroughTheRealSharedMapperAndReturnTheGeneratedId() throws Exception {
        Map<String, String> schema = schema();
        Properties props = values(schema);
        schema.forEach((name, type) -> { if (type.equals("BIT")) props.setProperty(name, props.getProperty(name).equals("1") ? "on" : "off"); });
        Connection connection = mock(Connection.class);
        PreparedStatement insert = mock(PreparedStatement.class), id = mock(PreparedStatement.class);
        ResultSet row = savedRow(schema, props), generated = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT * FROM formAlpha WHERE demographic_no=? AND ID=0", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)).thenReturn(insert);
        when(connection.prepareStatement("SELECT LAST_INSERT_ID()", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)).thenReturn(id);
        when(insert.executeQuery()).thenReturn(row);
        when(id.executeQuery()).thenReturn(generated);
        when(generated.next()).thenReturn(true);
        when(generated.getInt(1)).thenReturn(812);
        jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
        jdbc.when(() -> LegacyJdbcQuery.trustedSelectSql(anyString())).thenCallRealMethod();
        CarlosProperties config = mock(CarlosProperties.class);
        when(config.getProperty("db_type", "")).thenReturn("mysql");
        when(config.getProperty("save_as_xml", "false")).thenReturn("false");
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(config);
            assertThat(record.saveFormRecord(props)).isEqualTo(812);
        }
        verify(insert).setObject(1, "123");
        verify(row).moveToInsertRow();
        verify(row).insertRow();
        for (var col : schema.entrySet()) {
            String name = col.getKey();
            if (name.equals("ID")) verify(row).updateInt(name, 0);
            else if (col.getValue().equals("BIT")) verify(row).updateInt(name, props.getProperty(name).equals("on") ? 1 : 0);
            else if (col.getValue().equals("date")) verify(row).updateDate(eq(name), any(java.sql.Date.class));
            else if (col.getValue().equals("timestamp")) verify(row).updateTimestamp(eq(name), any(java.sql.Timestamp.class));
            else verify(row).updateString(name, props.getProperty(name));
        }
        verify(connection).close();
    }

    @Test
    void shouldPropagateDatabaseFailuresInsteadOfReturningAnApparentlyBlankSavedForm() throws Exception {
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet("SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?", 123, 99)).thenThrow(new SQLException("FAKE failed read"));
        assertThatThrownBy(() -> record.getFormRecord(null, 123, 99)).isInstanceOf(SQLException.class).hasMessage("FAKE failed read");
    }

    @Test
    void shouldRestoreOnlyTheExplicitAlphaFactoryClassAndKeepUnknownClassesRejected() {
        assertThat(new FrmRecordFactory().factory("Alpha")).isInstanceOf(FrmAlphaRecord.class);
        assertThat(new FrmRecordFactory().factory("UnsupportedLegacyRecord")).isNull();
        assertThat(new FrmRecordFactory().factory("Alpha../Other")).isNull();
    }

    @Test
    void shouldRetainTheExistingSavePrintAndExitActionContract() throws Exception {
        assertThat(record.findActionValue("SAVE")).isEqualTo("save");
        assertThat(record.findActionValue("PRINT")).isEqualTo("print");
        assertThat(record.findActionValue("exit")).isEqualTo("exit");
        assertThat(record.createActionURL("formalpha.jsp", "save", "123", "99")).isEqualTo("formalpha.jsp?demographic_no=123&formId=99");
        assertThat(record.createActionURL("close", "exit", "123", "99")).isEqualTo("close");
    }

    private Map<String, String> schema() throws Exception {
        String source = Files.readString(Path.of("database/mysql/migration/common/V1__baseline_schema.sql"));
        String table = source.substring(source.indexOf("CREATE TABLE `formAlpha` ("));
        table = table.substring(0, table.indexOf("PRIMARY KEY"));
        Map<String, String> columns = new LinkedHashMap<>();
        var fields = Pattern.compile("`([^`]+)` (int|varchar|text|tinyint|date|timestamp)\\b").matcher(table);
        while (fields.find()) columns.put(fields.group(1), fields.group(2).equals("tinyint") ? "BIT" : fields.group(2));
        assertThat(columns.size()).isGreaterThan(45);
        return columns;
    }

    private Properties values(Map<String, String> schema) {
        Properties props = new Properties();
        int index = 0;
        for (var col : schema.entrySet()) {
            String value = switch (col.getValue()) {
                case "BIT" -> index % 2 == 0 ? "1" : "0";
                case "date" -> "2026/10/21";
                case "timestamp" -> "2026/10/21 12:00:00";
                default -> "FAKE " + col.getKey() + " clinical suffix </textarea>&";
            };
            props.setProperty(col.getKey(), value); index++;
        }
        props.setProperty("ID", "99"); props.setProperty("demographic_no", "123"); props.setProperty("provider_no", "17");
        return props;
    }

    private ResultSet savedRow(Map<String, String> schema, Properties props) throws Exception {
        ResultSet row = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(row.next()).thenReturn(true, false);
        when(row.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(schema.size());
        int index = 0;
        for (var col : schema.entrySet()) {
            int i = ++index;
            when(metadata.getColumnName(i)).thenReturn(col.getKey());
            when(metadata.getColumnTypeName(i)).thenReturn(col.getValue());
            when(metadata.getScale(i)).thenReturn(col.getValue().equals("BIT") ? 1 : 0);
            when(row.getString(i)).thenReturn(props.getProperty(col.getKey()));
            when(row.getInt(i)).thenReturn(props.getProperty(col.getKey()).equals("1") ? 1 : 0);
            when(row.getDate(i)).thenReturn(java.sql.Date.valueOf("2026-10-21"));
            when(row.getTimestamp(i)).thenReturn(java.sql.Timestamp.valueOf("2026-10-21 12:00:00"));
        }
        return row;
    }
}
