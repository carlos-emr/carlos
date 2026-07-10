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
package io.github.carlos_emr.carlos.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link FlywaySchemaValidator}'s pure, no-database logic: mode normalization,
 * location splitting, and the two boot guards that fail loud before Flyway ever opens a connection.
 *
 * <p>These pin the class's safety properties — a mistyped mode must abort boot rather than silently
 * disable the schema gate, and an enabled gate with nothing to validate against must abort — so a
 * future refactor cannot regress them unnoticed. H2-backed tests also pin the strict Flyway
 * behavior that matters before dialect-specific MariaDB schema tests run in CI.</p>
 *
 * @since 2026-07-09
 */
@Tag("unit")
@Tag("database")
@DisplayName("FlywaySchemaValidator boot gate")
class FlywaySchemaValidatorUnitTest extends CarlosUnitTestBase {

    private static final String ON_LOCATIONS =
            "classpath:db/migration/common,classpath:db/migration/on";
    private static final String BC_LOCATIONS =
            "classpath:db/migration/common,classpath:db/migration/bc";

    @Test
    @DisplayName("shouldDeriveLocationsFromBillregion_whenLocationsBlank")
    void shouldDeriveLocationsFromBillregion_whenLocationsBlank() {
        assertThat(FlywaySchemaValidator.resolveLocations("", " bc "))
                .containsExactly("classpath:db/migration/common", "classpath:db/migration/bc");
    }

    @Test
    @DisplayName("shouldRejectLocationsThatDoNotMatchBillregion")
    void shouldRejectLocationsThatDoNotMatchBillregion() {
        assertThatThrownBy(() -> FlywaySchemaValidator.resolveLocations(ON_LOCATIONS, "BC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billregion=BC")
                .hasMessageContaining("db/migration/bc");
    }

    @Test
    @DisplayName("shouldAcceptLocationsThatMatchBillregion")
    void shouldAcceptLocationsThatMatchBillregion() {
        assertThat(FlywaySchemaValidator.resolveLocations(BC_LOCATIONS, "BC"))
                .containsExactly("classpath:db/migration/common", "classpath:db/migration/bc");
    }

    @Test
    @DisplayName("shouldDefaultToOff_forNullMode")
    void shouldDefaultToOff_forNullMode() {
        DataSource dataSource = mock(DataSource.class);
        FlywaySchemaValidator validator = new FlywaySchemaValidator(dataSource, null, ON_LOCATIONS);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        // off mode is a no-op: the schema is managed out of band, so Flyway/the pool are never touched.
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldDefaultToOff_forBlankMode")
    void shouldDefaultToOff_forBlankMode() {
        DataSource dataSource = mock(DataSource.class);
        FlywaySchemaValidator validator = new FlywaySchemaValidator(dataSource, "   ", ON_LOCATIONS);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldSkipValidation_whenModeOff")
    void shouldSkipValidation_whenModeOff() {
        DataSource dataSource = mock(DataSource.class);
        FlywaySchemaValidator validator = new FlywaySchemaValidator(dataSource, "off", ON_LOCATIONS);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldNormalizeMode_withMixedCaseAndWhitespace")
    void shouldNormalizeMode_withMixedCaseAndWhitespace() {
        DataSource dataSource = mock(DataSource.class);
        // "  VALIDATE " must normalize to the validate mode (trim + Locale.ROOT lowercase). With a
        // valid location set the guard passes and Flyway is invoked, which touches the DataSource —
        // proving the token was accepted as validate rather than falling through to off.
        FlywaySchemaValidator validator =
                new FlywaySchemaValidator(dataSource, "  VALIDATE ", ON_LOCATIONS);

        // No live database, so Flyway's own validation surfaces as an exception — the point is that
        // it was reached at all (validate mode was recognised), not the specific failure.
        assertThatThrownBy(validator::afterPropertiesSet).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("shouldRejectBoot_forUnknownMode")
    void shouldRejectBoot_forUnknownMode() {
        DataSource dataSource = mock(DataSource.class);

        // The safety property: a typo must fail loud, not silently downgrade to off and disable the
        // schema gate. The constructor normalizes eagerly, so the throw happens on construction.
        assertThatThrownBy(() -> new FlywaySchemaValidator(dataSource, "valdiate", ON_LOCATIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("off|validate|migrate");
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldFailBoot_whenLocationsBlankInValidateMode")
    void shouldFailBoot_whenLocationsBlankInValidateMode() {
        DataSource dataSource = mock(DataSource.class);
        FlywaySchemaValidator validator = new FlywaySchemaValidator(dataSource, "validate", "");

        // An enabled gate with nothing to validate against is a broken config, not a reason to
        // silently skip the gate the operator turned on.
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carlos.flyway.locations");
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldTreatWhitespaceOnlyLocationsAsEmpty_inMigrateMode")
    void shouldTreatWhitespaceOnlyLocationsAsEmpty_inMigrateMode() {
        DataSource dataSource = mock(DataSource.class);
        // splitLocations drops blank/whitespace segments, so " , , " yields no locations and trips
        // the same empty-locations guard — proving the split filters empties rather than passing
        // through phantom entries.
        FlywaySchemaValidator validator = new FlywaySchemaValidator(dataSource, "migrate", " , , ");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carlos.flyway.locations");
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("shouldFailBoot_whenMigrationLocationMissing")
    void shouldFailBoot_whenMigrationLocationMissing(@TempDir Path tempDir) {
        JdbcDataSource dataSource = h2DataSource("missing_location");
        Path missing = tempDir.resolve("does-not-exist");
        FlywaySchemaValidator validator = new FlywaySchemaValidator(
                dataSource, "validate", "filesystem:" + missing.toAbsolutePath());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Failed to find filesystem location");
    }

    @Test
    @DisplayName("shouldFailBoot_whenDatabaseHasFutureMigration")
    void shouldFailBoot_whenDatabaseHasFutureMigration(@TempDir Path tempDir) throws Exception {
        JdbcDataSource dataSource = h2DataSource("future_migration");
        Path applied = tempDir.resolve("applied");
        Path shipped = tempDir.resolve("shipped");
        Files.createDirectories(applied);
        Files.createDirectories(shipped);
        String v1 = "CREATE TABLE patient (id INT PRIMARY KEY);\n";
        Files.writeString(applied.resolve("V1__base.sql"), v1);
        Files.writeString(applied.resolve("V2__future.sql"), "CREATE TABLE future_only (id INT PRIMARY KEY);\n");
        Files.writeString(shipped.resolve("V1__base.sql"), v1);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + applied.toAbsolutePath())
                .load()
                .migrate();

        FlywaySchemaValidator validator = new FlywaySchemaValidator(
                dataSource, "validate", "filesystem:" + shipped.toAbsolutePath());

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Validate failed");
    }

    private JdbcDataSource h2DataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

}
