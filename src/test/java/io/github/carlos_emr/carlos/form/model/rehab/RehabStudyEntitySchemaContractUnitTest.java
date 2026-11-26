/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model.rehab;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.form.RehabStudyFormType;
import io.github.carlos_emr.carlos.form.RehabStudyForms;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Each generated questionnaire entity maps every column of its unchanged Flyway baseline table,
 * once, with the physical type, and its column records read and write that column's own field.
 *
 * <p>The entities come from {@code scripts/forms/generate_rehab_study_entities.py}. A hand edit,
 * a stale regeneration or a later migration that changes one of these tables fails here.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("form")
@DisplayName("Rehabilitation study entities match the baseline tables")
class RehabStudyEntitySchemaContractUnitTest {
    private static final Path BASELINE = Path.of("database/mysql/migration/common/V1__baseline_schema.sql");
    private static final Path MIGRATIONS = Path.of("database/mysql/migration");
    private static final Pattern KEYS_TOGGLE =
            Pattern.compile("(?i)/\\*!40000 ALTER TABLE `[A-Za-z0-9_]+` (DISABLE|ENABLE) KEYS \\*/;");
    private static final Pattern COLUMN = Pattern.compile("^\\s*`([^`]+)`\\s+(\\S+)(.*?),?\\s*$");
    private static final Map<String, String> HEADER = Map.of(
            "ID", "int(10)", "demographic_no", "int(10)", "provider_no", "int(10)",
            "formCreated", "date", "formEdited", "timestamp", "studyID", "varchar(20)");

    private record PhysicalColumn(String name, String type, String rest) { }

    static Stream<Arguments> questionnaires() {
        return RehabStudyForms.all().stream().map(type -> Arguments.of(type.formClass(), type));
    }

    private static List<PhysicalColumn> baselineColumns(String table) throws IOException {
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8);
        String opening = "CREATE TABLE `" + table + "` (";
        int start = baseline.indexOf(opening);
        assertThat(start).as("CREATE TABLE of %s", table).isNotNegative();
        String body = baseline.substring(start + opening.length(), baseline.indexOf("\n)", start));
        List<PhysicalColumn> columns = new ArrayList<>();
        for (String line : body.split("\n")) {
            Matcher matcher = COLUMN.matcher(line);
            if (line.strip().startsWith("`") && matcher.matches()) {
                columns.add(new PhysicalColumn(matcher.group(1), matcher.group(2).toLowerCase(Locale.ROOT),
                        matcher.group(3).strip()));
            }
        }
        return columns;
    }

    private static Map<String, Field> answerFields(Class<?> entity) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : entity.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                assertThat(fields.put(column.name(), field)).as("duplicate mapping of %s", column.name()).isNull();
            } else {
                assertThat(Modifier.isStatic(field.getModifiers())).as("unmapped instance field %s", field).isTrue();
            }
        }
        return fields;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionnaires")
    void shouldMapEveryBaselineColumnOnce_withItsPhysicalType(String formClass, RehabStudyFormType<?> type)
            throws Exception {
        Class<?> entity = type.entity();
        assertThat(entity.getAnnotation(Entity.class)).isNotNull();
        assertThat(entity.getSuperclass()).isEqualTo(RehabStudyAssessment.class);
        assertThat(entity.getAnnotation(Table.class).name()).isEqualTo(type.table());
        List<PhysicalColumn> physical = baselineColumns(type.table());
        List<PhysicalColumn> header = physical.stream().filter(c -> HEADER.containsKey(c.name())).toList();
        assertThat(header).extracting(PhysicalColumn::name).containsExactlyInAnyOrderElementsOf(HEADER.keySet());
        for (PhysicalColumn column : header) {
            assertThat(column.type()).as("%s.%s", type.table(), column.name()).isEqualTo(HEADER.get(column.name()));
        }

        List<String> answers = physical.stream().filter(c -> !HEADER.containsKey(c.name()))
                .map(PhysicalColumn::name).toList();
        Map<String, Field> fields = answerFields(entity);
        assertThat(fields.keySet()).as("mapped answer columns of %s, in physical order", type.table())
                .containsExactlyElementsOf(answers);
        assertThat(type.columns()).extracting(RehabStudyColumn::name).containsExactlyElementsOf(answers);
        for (PhysicalColumn answer : physical.stream().filter(c -> !HEADER.containsKey(c.name())).toList()) {
            Field field = fields.get(answer.name());
            Column column = field.getAnnotation(Column.class);
            assertThat(answer.rest()).as("%s.%s nullability", type.table(), answer.name())
                    .isEqualToIgnoringCase("DEFAULT NULL");
            assertThat(column.columnDefinition()).as("%s.%s type", type.table(), answer.name()).isEqualTo(answer.type());
            assertThat(column.nullable()).isTrue();
            Class<?> expected = switch (answer.type()) {
                case "tinyint(1)" -> Byte.class;
                case "date" -> Date.class;
                default -> String.class;
            };
            assertThat(field.getType()).as("%s.%s Java type", type.table(), answer.name()).isEqualTo(expected);
            if (expected == Date.class) {
                assertThat(field.getAnnotation(Temporal.class).value()).isEqualTo(TemporalType.DATE);
            } else if (expected == String.class) {
                // Numeric answers stay the posted text, as the retired records bound them.
                assertThat(answer.type()).matches("(varchar|char|int)\\(\\d+\\)|double");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("questionnaires")
    void shouldBindEveryColumnRecordToItsOwnField(String formClass, RehabStudyFormType<?> type) throws Exception {
        assertBindings(type);
    }

    private static <E extends RehabStudyAssessment> void assertBindings(RehabStudyFormType<E> type) throws Exception {
        Map<String, Field> fields = answerFields(type.entity());
        for (Field field : fields.values()) field.setAccessible(true);
        for (RehabStudyColumn<E> column : type.columns()) {
            E form = type.factory().get();
            Object written = switch (column) {
                case RehabStudyColumn.Checkbox<E> checkbox -> {
                    checkbox.writer().accept(form, (byte) 1);
                    yield checkbox.reader().apply(form);
                }
                case RehabStudyColumn.Text<E> text -> {
                    text.writer().accept(form, "value of " + column.name());
                    yield text.reader().apply(form);
                }
                case RehabStudyColumn.Day<E> day -> {
                    day.writer().accept(form, new Date(0));
                    yield day.reader().apply(form);
                }
            };
            assertThat(written).as("%s read back", column.name()).isNotNull();
            for (Map.Entry<String, Field> field : fields.entrySet()) {
                Object value = field.getValue().get(form);
                if (field.getKey().equals(column.name())) {
                    assertThat(value).as("%s writes its own field", column.name()).isEqualTo(written);
                } else {
                    assertThat(value).as("%s leaves %s untouched", column.name(), field.getKey()).isNull();
                }
            }
        }
    }

    @Test
    void shouldHaveNoLaterMigrationThatChangesAQuestionnaireTable() throws IOException {
        List<String> tables = RehabStudyForms.all().stream().map(RehabStudyFormType::table).toList();
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                if (file.endsWith(BASELINE.getFileName())) continue;
                // The province data files only toggle the empty tables' keys while loading
                // (mysqldump's DISABLE/ENABLE KEYS) and register the forms in encounterForm.
                String sql = KEYS_TOGGLE.matcher(Files.readString(file, StandardCharsets.UTF_8)).replaceAll("");
                for (String table : tables) {
                    // Any other DDL on these tables would change the mapping.
                    Pattern ddl = Pattern.compile("(?i)\\b(ALTER|CREATE|DROP|RENAME)\\s+TABLE\\s+"
                            + "(IF\\s+(NOT\\s+)?EXISTS\\s+)?`?" + table + "`?(\\s|;|\\()");
                    assertThat(ddl.matcher(sql).find()).as("%s changes %s", file, table).isFalse();
                }
            }
        }
    }

    @Test
    void shouldMapTheSharedHeader_inTheSuperclass() {
        Map<String, String> mapped = new LinkedHashMap<>();
        Arrays.stream(RehabStudyAssessment.class.getDeclaredFields()).filter(f -> f.getAnnotation(Column.class) != null)
                .forEach(f -> mapped.put(f.getAnnotation(Column.class).name(), f.getType().getSimpleName()));
        assertThat(mapped).containsExactlyInAnyOrderEntriesOf(Map.of("ID", "Integer", "demographic_no", "Integer",
                "provider_no", "Integer", "formCreated", "Date", "formEdited", "Date", "studyID", "String"));
    }
}
