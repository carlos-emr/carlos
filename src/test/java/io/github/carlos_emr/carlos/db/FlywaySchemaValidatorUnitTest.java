/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link FlywaySchemaValidator}'s pure, no-database logic: mode normalization,
 * location splitting, and the two boot guards that fail loud before Flyway ever opens a connection.
 *
 * <p>These pin the class's safety properties — a mistyped mode must abort boot rather than silently
 * disable the schema gate, and an enabled gate with nothing to validate against must abort — so a
 * future refactor cannot regress them unnoticed. The {@code validate}-mode "database is behind the
 * WAR aborts boot" path needs a real MariaDB/MySQL with a {@code flyway_schema_history} table and is
 * covered by the CI Flyway gate ({@code .github/workflows/db-schema-verify.yml}), not here.</p>
 *
 * @since 2026-07-09
 */
@Tag("unit")
@Tag("database")
@DisplayName("FlywaySchemaValidator boot gate")
class FlywaySchemaValidatorUnitTest extends CarlosUnitTestBase {

    private static final String ON_LOCATIONS =
            "classpath:db/migration/common,classpath:db/migration/on";

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
}
