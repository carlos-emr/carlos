/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards development HRM cleanup and document-fixture validation.
 *
 * @since 2026-08-12
 */
@Tag("unit")
@DisplayName("development HRM fixture seed regressions")
class HrmDevelopmentFixtureSeedRegressionTest {

    private static final Path CLEANUP_SQL = Path.of(
            ".devcontainer", "db", "scripts", "development_hrm_cleanup.sql");
    private static final Path DATABASE_DOCKERFILE =
            Path.of(".devcontainer", "db", "Dockerfile");
    private static final Path POPULATE_SCRIPT =
            Path.of(".devcontainer", "db", "scripts", "populate_db.sh");
    private static final Path SEED_SCRIPT =
            Path.of(".devcontainer", "development", "setup", "seed_data.sh");
    private static final Path VALIDATOR = Path.of(
            ".devcontainer", "development", "setup", "validate_hrm_fixtures.sh");

    @TempDir
    Path fixtureDirectory;

    @Test
    @DisplayName("should remove only undistributed snapshot HRM rows")
    void shouldRemoveOnlyUndistributedSnapshotHrmRows_whenDevelopmentDataIsSeeded()
            throws IOException {
        String cleanupSql = Files.readString(CLEANUP_SQL, StandardCharsets.UTF_8);

        assertThat(cleanupSql)
                .contains("`id` BETWEEN 1 AND 41")
                .contains("`timeReceived` >= '2023-07-25 00:00:00'")
                .contains("`timeReceived` < '2023-09-06 00:00:00'")
                .contains("`reportFile` LIKE 'LabUpload.%'")
                .contains(
                        "FROM `HRMDocumentComment`",
                        "FROM `HRMDocumentSubClass`",
                        "FROM `HRMDocumentToDemographic`",
                        "FROM `HRMDocumentToProvider`",
                        "FROM `HRMDocument`");
    }

    @Test
    @DisplayName("should apply HRM cleanup to fresh and existing development databases")
    void shouldApplyHrmCleanup_toFreshAndExistingDevelopmentDatabases() throws IOException {
        String dockerfile = Files.readString(DATABASE_DOCKERFILE, StandardCharsets.UTF_8);
        String populateScript = Files.readString(POPULATE_SCRIPT, StandardCharsets.UTF_8);
        String seedScript = Files.readString(SEED_SCRIPT, StandardCharsets.UTF_8);

        assertThat(dockerfile).contains(
                "COPY ./.devcontainer/db/scripts/development_hrm_cleanup.sql "
                        + "/scripts/development_hrm_cleanup.sql");
        assertThat(populateScript)
                .contains("$SQL oscar < /scripts/development_hrm_cleanup.sql")
                .satisfies(script -> assertThat(script.indexOf("development_hrm_cleanup.sql"))
                        .isGreaterThan(script.indexOf("development.sql")));
        assertThat(seedScript)
                .contains("< /workspace/.devcontainer/db/scripts/development_hrm_cleanup.sql")
                .contains("validate_hrm_fixtures.sh")
                .contains("SELECT reportFile FROM HRMDocument");
    }

    @Test
    @DisplayName("should accept seeded HRM references when every fixture exists")
    void shouldAcceptSeededHrmReferences_whenEveryFixtureExists() throws Exception {
        Files.writeString(fixtureDirectory.resolve("present.xml"), "<hrm/>", StandardCharsets.UTF_8);

        ValidationResult result = runValidator("present.xml\n");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Validated seeded HRM document references");
    }

    @Test
    @DisplayName("should reject seeded HRM references when a fixture is missing")
    void shouldRejectSeededHrmReferences_whenFixtureIsMissing() throws Exception {
        ValidationResult result = runValidator("missing.xml\n");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output())
                .contains("Missing seeded HRM fixture: missing.xml")
                .contains("1 seeded HRM report(s) have no document fixture");
    }

    private ValidationResult runValidator(String reportFiles) throws Exception {
        Process process = new ProcessBuilder("sh", VALIDATOR.toString(), fixtureDirectory.toString())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().write(reportFiles.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return new ValidationResult(
                process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record ValidationResult(int exitCode, String output) {
    }
}
