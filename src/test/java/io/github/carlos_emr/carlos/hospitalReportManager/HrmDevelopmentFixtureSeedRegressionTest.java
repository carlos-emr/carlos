/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.IOException;
import java.net.URISyntaxException;
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

    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path CLEANUP_SQL = PROJECT_ROOT.resolve(Path.of(
            ".devcontainer", "db", "scripts", "development_hrm_cleanup.sql"));
    private static final Path DATABASE_DOCKERFILE = PROJECT_ROOT.resolve(
            Path.of(".devcontainer", "db", "Dockerfile"));
    private static final Path POPULATE_SCRIPT = PROJECT_ROOT.resolve(
            Path.of(".devcontainer", "db", "scripts", "populate_db.sh"));
    private static final Path SEED_SCRIPT = PROJECT_ROOT.resolve(
            Path.of(".devcontainer", "development", "setup", "seed_data.sh"));
    private static final Path VALIDATOR = PROJECT_ROOT.resolve(Path.of(
            ".devcontainer", "development", "setup", "validate_hrm_fixtures.sh"));

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
                        "FROM `HRMDocument`")
                .contains("SET hrm_document.`parentReport` = NULL");
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
        Files.createDirectories(documentDirectory());
        Files.writeString(documentDirectory().resolve("present.xml"), "<hrm/>", StandardCharsets.UTF_8);

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

    @Test
    @DisplayName("should reject seeded HRM references outside the document directory")
    void shouldRejectSeededHrmReferences_whenPathEscapesDocumentDirectory() throws Exception {
        Files.writeString(fixtureDirectory.resolve("outside.xml"), "<hrm/>", StandardCharsets.UTF_8);

        ValidationResult result = runValidator("../outside.xml\n");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output())
                .contains("Invalid seeded HRM fixture path outside document directory")
                .contains("1 seeded HRM report(s) have invalid fixture paths")
                .doesNotContain("have no document fixture");
    }

    @Test
    @DisplayName("should report invalid paths separately from missing fixtures")
    void shouldReportInvalidPathsSeparately_whenFixtureIsAlsoMissing() throws Exception {
        ValidationResult result = runValidator("../outside.xml\nmissing.xml\n");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output())
                .contains("1 seeded HRM report(s) have invalid fixture paths")
                .contains("1 seeded HRM report(s) have no document fixture");
    }

    @Test
    @DisplayName("should reject validator invocation without an explicit report list")
    void shouldRejectValidatorInvocation_withoutExplicitReportList() throws Exception {
        Files.createDirectories(documentDirectory());
        Process process = new ProcessBuilder("sh", VALIDATOR.toString(),
                documentDirectory().toString())
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).isNotZero();
        assertThat(output).contains("REPORT_FILE_LIST");
    }

    private ValidationResult runValidator(String reportFiles) throws Exception {
        Files.createDirectories(documentDirectory());
        Path reportFileList = fixtureDirectory.resolve("hrm-report-files.txt");
        Files.writeString(reportFileList, reportFiles, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("sh", VALIDATOR.toString(),
                documentDirectory().toString(), reportFileList.toString())
                .redirectErrorStream(true)
                .start();

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return new ValidationResult(
                process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private Path documentDirectory() {
        return fixtureDirectory.resolve("documents");
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(HrmDevelopmentFixtureSeedRegressionTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path current = Files.isRegularFile(location) ? location.getParent() : location;
            while (current != null) {
                if (Files.isRegularFile(current.resolve(
                        ".devcontainer/db/scripts/development_hrm_cleanup.sql"))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }

    private record ValidationResult(int exitCode, String output) {
    }
}
