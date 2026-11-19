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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import io.github.carlos_emr.carlos.form.model.FormRourke2017;
import io.github.carlos_emr.carlos.form.model.FormRourke2020;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The current Rourke 2017 or 2020 revision prints through the form's own Jasper pages from the
 * captured revision and checkbox rows, with the chart's name, birth date and FSA, in the bundled
 * DejaVu fonts; a value the pages cannot hold refuses the printed chart with its category.
 */
@Tag("unit")
@Tag("cds-export")
@Tag("report")
class CdsExportRourkeChartComposerUnitTest {
    private static Set<String> columns(String table) {
        var result = new TreeSet<String>();
        for (var column : CdsExportSourceCatalogue.current().physical(table).orElseThrow().columns())
            result.add(column.name());
        return result;
    }

    /**
     * A saved revision as the form stores one: text columns empty, numbers and dates absent, and
     * the save time set (the 2020 form's properties need one).
     */
    private static Row revision(String table, Map<String, String> values) {
        var row = new LinkedHashMap<String, Value>();
        for (var column : CdsExportSourceCatalogue.current().physical(table).orElseThrow().columns())
            row.put(column.name(), column.type().matches("(?:var)?char.*|(?:tiny|medium|long)?text")
                    ? Value.text("") : Value.sqlNull());
        row.put("formEdited", Value.text("2024-02-01 10:00:00"));
        values.forEach((column, value) -> row.put(column, Value.text(value)));
        return new Row(table, CdsExportPatientTableSourceReader.key(12), row);
    }

    private static Row checkbox(String table, String field, String value) {
        var row = new LinkedHashMap<String, Value>();
        row.put("form_name", Value.text(table));
        row.put("form_id", Value.text("12"));
        row.put("field_name", Value.text(field));
        row.put("value", value == null ? Value.sqlNull() : Value.text(value));
        return new Row("form_boolean_value", "00000000000000000001" + field, row);
    }

    private static Group group(String table, int revision, int revisions, Map<String, String> values) {
        var rows = new ArrayList<Row>();
        rows.add(revision(table, values));
        rows.add(checkbox(table, "p1_breastFeeding1wOk", "1"));
        return new Group(Kind.ENCOUNTER_FORM, table, "12", rows, revision, revisions, null, false);
    }

    private static Map<String, String> demographic(String lastName) {
        var demographic = new HashMap<String, String>();
        demographic.putAll(Map.of("demographic_no", "27", "last_name", lastName, "first_name", "BABY",
                "year_of_birth", "2024", "month_of_birth", "01", "date_of_birth", "15", "postal", "M5V 2T6"));
        return demographic;
    }

    private static CdsExportDataSummaryOccurrenceService.Plan plan(Group group, String lastName) {
        var demographic = demographic(lastName);
        return new CdsExportDataSummaryOccurrenceService.Plan(
                List.of(group), CdsExportDataSummaryHtmlComposer.Patient.of(demographic), demographic);
    }

    private static CdsExportRourkeChartComposer composer() {
        var references = mock(CdsExportPrintedFormReadDao.class);
        when(references.provider("101")).thenReturn(new CdsExportPrintedFormReadDao.Provider("Pat", "PEDIATRIC", "1"));
        return new CdsExportRourkeChartComposer(references);
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldMapEveryPersistentFormField_toAStoredColumn(String table) {
        Class<?> type = table.equals("formRourke2017") ? FormRourke2017.class : FormRourke2020.class;
        var physical = columns(table);
        var missing = new ArrayList<String>();
        for (var field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)
                    || Map.class.isAssignableFrom(field.getType())) continue;
            var column = field.getAnnotation(Column.class);
            String name = column != null && !column.name().isEmpty() ? column.name() : field.getName();
            if (!physical.contains(name)) missing.add(name);
        }
        assertThat(missing).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldFillTheFormProperties_fromTheCaptureAndTheChart(String table) throws Exception {
        var group = group(table, 1, 1, Map.of("ID", "12", "demographic_no", "27", "provider_no", "101",
                "formCreated", "2024-02-01", "formEdited", "2024-02-01 10:00:00", "c_pName", "OLD, NAME",
                "c_birthWeight", "3.4", "p1_date1w", "2024-01-22"));

        var props = CdsExportRourkeChartComposer.properties(group, demographic("FIXTURE"));

        assertThat(props.getProperty("c_pName")).isEqualTo("FIXTURE, BABY");
        assertThat(props.getProperty("c_birthDate")).isEqualTo("15/01/2024");
        assertThat(props.getProperty("c_fsa")).isEqualTo("M5V");
        assertThat(props.getProperty("c_birthWeight")).isEqualTo("3.4");
        assertThat(props.getProperty("p1_date1w")).isEqualTo("22/01/2024");
        assertThat(props.getProperty("p1_breastFeeding1wOk")).isEqualTo("checked='checked'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldPrintTheCurrentRevision_throughTheFormsJasperPages(String table) throws Exception {
        var group = group(table, 2, 2, Map.of("ID", "12", "demographic_no", "27", "provider_no", "101",
                "formCreated", "2024-02-01", "c_birthWeight", "3.4"));

        var printed = composer().print(group, plan(group, "FIXTURE"));

        assertThat(printed.subClass()).startsWith("Rourke Baby Record 20");
        assertThat(printed.event().lexical()).isEqualTo("2024-02-01");
        assertThat(printed.author()).isEqualTo("PEDIATRIC, Pat");
        assertThat(printed.metadata()).containsOnlyKeys("formCreated", "provider_no");
        try (var pdf = Loader.loadPDF(printed.pdf())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(4);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("FIXTURE, BABY", "3.4");
            // A value the revision does not hold (here every visit date) prints blank, never the
            // word "null": the 2017 pages' text fields lacked blankWhenNull (census finding).
            assertThat(text).doesNotContainPattern("\\bnull\\b");
        }        // The same revision printed again gives the same bytes once made reproducible.
        var again = composer().print(group, plan(group, "FIXTURE"));
        String seed = "c".repeat(64);
        assertThat(CdsExportPrintedPdf.reproducible(again.pdf(), seed))
                .isEqualTo(CdsExportPrintedPdf.reproducible(printed.pdf(), seed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldRefuseThePrintedChart_whenTheNameCannotFitItsField(String table) {
        var group = group(table, 1, 1, Map.of("ID", "12", "demographic_no", "27", "formCreated", "2024-02-01"));

        assertThatThrownBy(() -> composer().print(group, plan(group, "LONG".repeat(60))))
                .isInstanceOf(CdsExportDataSummaryOccurrenceService.PrintRefusal.class)
                .hasMessage("TEXT_OVERFLOW");
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldPrintOnlyTheLatestRevision_forTheFormReadRight(String table) {
        assertThat(composer().prints(group(table, 3, 3, Map.of("ID", "12")))).isTrue();
        assertThat(composer().prints(group(table, 2, 3, Map.of("ID", "12")))).isFalse();
        assertThat(composer().prints(group("formRourke2009", 1, 1, Map.of()))).isFalse();
        assertThat(composer().readObject()).isEqualTo("_form");
    }

    @ParameterizedTest
    @ValueSource(strings = {"formRourke2017", "formRourke2020"})
    void shouldRefuseThePrintedChart_whenAStoredColumnIsMissing(String table) {
        var whole = revision(table, Map.of("ID", "12"));
        var values = new LinkedHashMap<>(whole.values());
        values.remove("c_birthWeight");
        var group = new Group(Kind.ENCOUNTER_FORM, table, "12", List.of(new Row(table, whole.recordKey(), values)),
                1, 1, null, false);

        assertThatThrownBy(() -> CdsExportRourkeChartComposer.properties(group, demographic("FIXTURE")))
                .isInstanceOf(CdsExportDataSummaryOccurrenceService.PrintRefusal.class)
                .hasMessage("FORM_MAPPING_MISMATCH");
    }
}
