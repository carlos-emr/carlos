/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.xml.bind.JAXBContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.data.EFormApConfig;
import io.github.carlos_emr.carlos.eform.util.EFormApCacheForPdfGenerationServlet;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/** Validates the shipped AP catalog at the same parameterization and SQL-safety boundary as rendering. */
@Tag("unit")
class BundledDatabaseApSqlUnitTest extends CarlosUnitTestBase {
    private static final Pattern SQL_LITERAL = Pattern.compile("'((?:[^']|'')*)'|\"((?:[^\"]|\"\")*)\"");

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    void shouldAcceptEveryBundledReadQuery_afterRequestParameterization() throws Exception {
        List<String> failures = new ArrayList<>();
        int queries = 0;
        for (DatabaseAP ap : catalog()) {
            if (ap.getApSQL() == null || ap.getApSQL().isBlank()) continue;
            queries++;
            ParameterizedSql query = new EForm().parameterizeAllFields(ap.getApSQL());
            try {
                EFormSqlSafety.validateLegacySqlSafety(query.getSql());
            } catch (SecurityException error) {
                failures.add(ap.getApName() + ": " + error.getMessage());
            }
        }
        assertThat(queries).isGreaterThan(300);
        assertThat(failures).as("Every bundled AP must pass the unchanged render-time safety validator").isEmpty();
    }

    @ParameterizedTest(name = "{0} preserves its authored line separators")
    @MethodSource("newlineDefinitions")
    void shouldPreserveNewlineLiteralValues_withoutSqlModeDependentEscapes(String name, List<String> expected) throws Exception {
        DatabaseAP ap = definition(name);
        ParameterizedSql query = new EForm().parameterizeAllFields(ap.getApSQL());
        assertThatCode(() -> EFormSqlSafety.validateLegacySqlSafety(query.getSql())).doesNotThrowAnyException();
        var matcher = SQL_LITERAL.matcher(query.getSql());
        List<String> lineSeparators = new ArrayList<>();
        while (matcher.find()) {
            String literal = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            if (literal.contains("\n")) lineSeparators.add(literal);
        }
        assertThat(lineSeparators).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "latest lab value preserves {0} through SQL, JSON and HTML fields")
    @MethodSource("clinicalLabValues")
    void shouldPreserveClinicalLabValues_withoutChangingMeaningOrCreatingMarkup(String name, String input, String expected) throws Exception {
        DatabaseAP ap = definition("latest_lab_values");
        String sql = new EForm().parameterizeAllFields(ap.getApSQL()).getSql();
        assertThatCode(() -> EFormSqlSafety.validateLegacySqlSafety(sql)).doesNotThrowAnyException();
        // Execute the exact projection shipped in the AP, rather than a duplicate
        // expression in the test. Bind clinical strings, including hostile content.
        var matcher = Pattern.compile("(?m)^\\s*(.*?)\\s*AS\\s+datafield\\s*,\\s*$", Pattern.CASE_INSENSITIVE).matcher(sql);
        assertThat(matcher.find()).isTrue();
        String json;
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:bundled_latest_lab;MODE=MySQL");
             var statement = connection.prepareStatement("SELECT JSON_OBJECT('measurementValue': " + matcher.group(1)
                     + ") FROM (SELECT CAST(? AS VARCHAR) AS dataField) m")) {
            statement.setString(1, input);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                json = rows.getString(1);
                assertThat(rows.next()).isFalse();
            }
        }
        var mapper = new ObjectMapper();
        var clinicalValue = mapper.readTree(json).get("measurementValue");
        if (expected == null) assertThat(clinicalValue.isNull()).isTrue();
        else assertThat(clinicalValue.textValue()).isEqualTo(expected);

        // The AP's JSON is data in either supported eForm container. Its quotes,
        // comparison operators and markup must survive as literal field content.
        for (String fieldType : List.of("hidden", "textarea")) {
            ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true, false);
            when(rows.getString("labvalues")).thenReturn(json);
            List<ParameterizedSql> executed = new ArrayList<>();
            try (var loader = mockStatic(EFormLoader.class);
                 var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS)) {
                loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
                loader.when(() -> EFormLoader.getAP("latest_lab_values")).thenReturn(ap);
                jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(ParameterizedSql.class)))
                        .thenAnswer(invocation -> { executed.add(invocation.getArgument(0)); return rows; });
                EForm form = new EForm();
                form.setDemographicNo("7");
                form.setFormHtml(fieldType.equals("textarea")
                        ? "<form><textarea name=LabValues oscarDB=latest_lab_values></textarea></form>"
                        : "<form><input type=hidden name=LabValues oscarDB=latest_lab_values></form>");
                form.setDatabaseAPs();
                var document = Jsoup.parse(form.getFormHtml());
                assertThat(document.select("img,script,[onerror],[onclick]")).isEmpty();
                var field = document.selectFirst("[name=LabValues]");
                assertThat(field).isNotNull();
                String roundtrip = fieldType.equals("textarea") ? field.wholeText() : field.val();
                assertThat(roundtrip).isEqualTo(json);
                assertThat(mapper.readTree(roundtrip).get("measurementValue")).isEqualTo(clinicalValue);
                assertThat(executed).hasSize(1);
                assertThat(executed.getFirst().getParams()).containsExactly("7");
            }
        }
    }

    private static Stream<Arguments> clinicalLabValues() {
        return Stream.of(
                Arguments.of("negative decimal", "-4.25", "-4.25"),
                Arguments.of("less than", "<5", "<5"),
                Arguments.of("greater than", ">100", ">100"),
                Arguments.of("less or equal", "<=0.2", "<=0.2"),
                Arguments.of("greater or equal", ">=15", ">=15"),
                Arguments.of("negative exponent", "1.2e-3", "1.2e-3"),
                Arguments.of("positive exponent", "+2.5E+4", "+2.5E+4"),
                Arguments.of("Unicode minus", "−0.25", "−0.25"),
                Arguments.of("Unicode less or equal", "≤0.5", "≤0.5"),
                Arguments.of("Unicode greater or equal", "≥1.5", "≥1.5"),
                Arguments.of("ratio", "1:40", "1:40"),
                Arguments.of("Unicode range", "3–5", "3–5"),
                Arguments.of("scientific multiplication", "1×10^-3", "1×10^-3"),
                Arguments.of("qualitative result", "not detected / below limit", "not detected / below limit"),
                Arguments.of("outer spaces", "  -4.25  ", "-4.25"),
                Arguments.of("hostile markup", "</textarea><img src=x onerror=\"alert(1)\"><script>alert(1)</script>& \" ' \\\n",
                        "</textarea><img src=x onerror=\"alert(1)\"><script>alert(1)</script>& \" ' \\\n"),
                Arguments.of("quotes and newline", "1'\"\\\n<bad>", "1'\"\\\n<bad>"),
                Arguments.of("self-referencing marker", "${labvalues}", "${labvalues}"),
                Arguments.of("another AP marker", "${medicalHistory}", "${medicalHistory}"),
                Arguments.of("unfinished marker", "${unfinished", "${unfinished"),
                Arguments.of("NULL", null, null));
    }

    @Test
    void shouldPreserveLiteralCrossAliasData_inBothBatchCallersAndThePdfCache() throws Exception {
        DatabaseAP ap = new DatabaseAP("literal_output", "SELECT ${demographic}", "${first}|${second}|${first}");
        String first = "${second}", second = "${first} $1\\tail${unfinished";
        var values = new ArrayList<>(List.of(first, second, first));
        String expected = first + "|" + second + "|" + first;
        try (var loader = mockStatic(EFormLoader.class);
             var util = mockStatic(EFormUtil.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("literal_output")).thenReturn(ap);
            util.when(() -> EFormUtil.getValuesOrNull(anyList(), any(ParameterizedSql.class))).thenReturn(values);
            util.when(() -> EFormUtil.getValues(anyList(), any(ParameterizedSql.class))).thenReturn(values);
            assertThat(new APExecute().execute("literal_output", "7")).isEqualTo(expected);
            assertThat(new APExecute().execute("literal_output", "7", 19)).isEqualTo(expected);
            EForm form = new EForm(); form.setDemographicNo("7");
            form.setFormHtml("<form><input type=hidden name=Literal oscarDB=literal_output></form>");
            form.setDatabaseAPs();
            assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("[name=Literal]").val()).isEqualTo(expected);
            var execute = EFormApCacheForPdfGenerationServlet.class.getDeclaredMethod("execute", EForm.class, DatabaseAP.class);
            execute.setAccessible(true);
            assertThat(execute.invoke(null, form, ap)).isEqualTo(expected);

            // Existing batch APIs ignore unused result columns for a constant output.
            ap.setApOutput("constant output");
            assertThat(new APExecute().execute("literal_output", "7")).isEqualTo("constant output");
            assertThat(new APExecute().execute("literal_output", "7", 19)).isEqualTo("constant output");
        }
    }

    @Test
    void shouldRenderMedicalHistory_insteadOfSilentlyClearingTheClinicalField() throws Exception {
        DatabaseAP ap = definition("medical_history");
        String history = "Synthetic first history\n Synthetic second & follow-up\n";
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("medicalHistory")).thenReturn(history);
        List<ParameterizedSql> executed = new ArrayList<>();
        try (var loader = mockStatic(EFormLoader.class);
             var jdbc = mockStatic(LegacyJdbcQuery.class, CALLS_REAL_METHODS)) {
            loader.when(EFormLoader::getMarker).thenReturn("oscarDB");
            loader.when(() -> EFormLoader.getAP("medical_history")).thenReturn(ap);
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(any(ParameterizedSql.class)))
                    .thenAnswer(invocation -> {
                        ParameterizedSql query = invocation.getArgument(0);
                        executed.add(query);
                        return rows;
                    });
            EForm form = new EForm();
            form.setDemographicNo("7");
            form.setFormHtml("<form><textarea name=\"MedicalHistory\" oscarDB=medical_history></textarea></form>");
            form.setDatabaseAPs();
            // Misc.getString deliberately trims the result's outer whitespace;
            // the clinical paragraphs and embedded newline must survive unchanged.
            assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("textarea[name=MedicalHistory]").wholeText()).isEqualTo(history.strip());
            assertThat(executed).hasSize(1);
            assertThat(executed.getFirst().getParams()).containsExactly("7", "7");
        }
    }

    private static Stream<Arguments> newlineDefinitions() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("bc_referral_address", List.of("\n", "\n"));
        definitions.put("bc_family_doctor_address", List.of("\n", "\n"));
        for (String name : List.of("social_family_history", "other_medications_history", "family_history",
                "medical_history", "ongoingconcerns", "past_ocular_history", "today_notes", "druglist")) {
            definitions.put(name, List.of("\n"));
        }
        definitions.put("allergies_des", List.of(" \n", ",\n\t\t\t"));
        definitions.put("recent_rx", List.of("\n\n", " \n"));
        definitions.put("today_rx", List.of("\n\n", " \n"));
        definitions.put("current_rx", List.of("\n", "\n"));
        definitions.put("current_rx_lt", List.of("\n", "\n"));
        definitions.put("druglist_generic", List.of(" \n"));
        return definitions.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    private static DatabaseAP definition(String name) throws Exception {
        return catalog().stream().filter(ap -> name.equals(ap.getApName())).findFirst().orElseThrow();
    }

    private static List<DatabaseAP> catalog() throws Exception {
        try (var stream = BundledDatabaseApSqlUnitTest.class.getResourceAsStream("/oscar/eform/apconfig.xml")) {
            assertThat(stream).isNotNull();
            var unmarshaller = JAXBContext.newInstance(EFormApConfig.class).createUnmarshaller();
            return ((EFormApConfig) unmarshaller.unmarshal(XmlUtils.createSecureJaxbSource(stream))).getDatabaseAPs();
        }
    }
}
