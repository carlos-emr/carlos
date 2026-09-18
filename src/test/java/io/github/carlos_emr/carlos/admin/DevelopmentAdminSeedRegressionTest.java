/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License;
 * you can redistribute it and/or modify it under the terms of the GPL.
 */
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Development Administration seed regressions")
@Tag("unit")
@Tag("admin")
class DevelopmentAdminSeedRegressionTest {

    private static final Path ADMIN_SEED = Path.of(
            ".devcontainer", "db", "scripts", "admin_test_data.sql");
    private static final Path ADMIN_ACCOUNT = Path.of(
            ".devcontainer", "db", "scripts", "admin_test_account.sql");
    private static final Path DB_DOCKERFILE = Path.of(
            ".devcontainer", "db", "Dockerfile");
    private static final Path POPULATE_DB = Path.of(
            ".devcontainer", "db", "scripts", "populate_db.sh");
    private static final Path DEV_PROPERTIES = Path.of(
            ".devcontainer", "development", "config", "shared", "volumes", "carlos.properties");
    private static final Path DEBIAN_RULES = Path.of("debian", "rules");
    private static final Path DEB_DBOPS = Path.of(
            "debian", "assets", "carlos_ctl", "dbops.py");

    /**
     * Surefire and IDEs do not agree on the working directory, so source-tree
     * fixtures are located by walking up from user.dir rather than assumed to sit
     * under the repo root. Mirrors StrutsAdminConfigTest in this package.
     */
    private static final int MAX_PARENT_SEARCH_DEPTH = 8;

    @Test
    @DisplayName("should load admin fixtures after the eForm migrations")
    void shouldLoadAdminFixtures_afterEformMigrations() throws IOException {
        String dockerfile = readProjectFile(DB_DOCKERFILE);
        String populate = readProjectFile(POPULATE_DB);

        assertThat(dockerfile)
                .contains("COPY ./.devcontainer/db/scripts/admin_test_data.sql /scripts/admin_test_data.sql")
                .contains("COPY ./.devcontainer/db/scripts/admin_test_account.sql /scripts/admin_test_account.sql");
        // Assert the path rather than the full command. populate_db.sh has carried
        // both a "$SQL oscar < ..." form and a literal "mysql -u root -p..." form,
        // and the load order is what this test actually cares about: the seed's
        // eForm group references the Rich Text Letter row the RTL chain installs,
        // and its labels must not be FAKE- prefixed by the name sanitization.
        assertThat(populate)
                .contains("/scripts/admin_test_data.sql")
                .satisfies(script -> assertThat(script.indexOf("/scripts/admin_test_data.sql"))
                        .isGreaterThan(script.indexOf("update-2026-03-22-rtl-2026.3.0-modernize.sql"))
                        .isGreaterThan(script.indexOf("update-2026-03-12-rtl-enable-direct.sql"))
                        .isGreaterThan(script.indexOf("update-2026-06-29-rtl-attachment-route-fix.sql"))
                        .isGreaterThan(script.indexOf("/scripts/demo-name-sanitization-on.sql")));
        // The login attaches to provider 999996, which the fixture file creates.
        assertThat(populate)
                .as("the locktest login must load after the provider row it references")
                .satisfies(script -> assertThat(script.indexOf("/scripts/admin_test_account.sql"))
                        .isGreaterThan(script.indexOf("/scripts/admin_test_data.sql")));
    }

    @Test
    @DisplayName("should ship the admin fixtures with the deb demo dataset, loaded last")
    void shouldShipAdminFixtures_withDebDemoData() throws IOException {
        String rules = readProjectFile(DEBIAN_RULES);
        String dbops = readProjectFile(DEB_DBOPS);

        assertThat(rules)
                .as("debian/rules must stage the seed next to the other demo pieces")
                .contains(".devcontainer/db/scripts/admin_test_data.sql \\");
        // carlos-ctl demo-data streams its pieces in list order; the seed has the
        // same ordering constraints as in populate_db.sh (after the RTL chain and
        // after the name sanitization), and the ON-only supplement is appended
        // conditionally, so the seed must come after that append too.
        assertThat(dbops)
                .contains("os.path.join(DEMO_DIR, \"admin_test_data.sql\")")
                .satisfies(source -> assertThat(source.indexOf("\"admin_test_data.sql\""))
                        .isGreaterThan(source.indexOf("\"update-2026-06-29-rtl-attachment-route-fix.sql\""))
                        .isGreaterThan(source.indexOf("\"demo-name-sanitization.sql\""))
                        .isGreaterThan(source.indexOf("\"demo-name-sanitization-on.sql\"")));
    }

    @Test
    @DisplayName("should keep the locktest login out of the shared fixtures and out of the deb")
    void shouldKeepLoginCredential_outOfDebDemoData() throws IOException {
        String seed = readProjectFile(ADMIN_SEED);
        String account = readProjectFile(ADMIN_ACCOUNT);
        String rules = readProjectFile(DEBIAN_RULES);
        String dbops = readProjectFile(DEB_DBOPS);

        // The deb demo load never introduces security rows (demo-additive-exclude
        // SEC section; the hash is the published dev hash bootstrap-admin removes).
        assertThat(seed)
                .as("the shared fixture file must carry no login credential")
                .doesNotContain("INSERT INTO security")
                .doesNotContain("INSERT INTO secUserRole")
                .doesNotContain("{bcrypt}");
        assertThat(account)
                .contains("'locktest'")
                .contains("INSERT INTO security")
                .contains("INSERT INTO secUserRole")
                .contains("WHERE NOT EXISTS");
        assertThat(rules).doesNotContain("admin_test_account.sql");
        assertThat(dbops).doesNotContain("admin_test_account.sql");
    }

    @Test
    @DisplayName("should lock the sacrificial account by username in the devcontainer")
    void shouldLockByUsername_inDevcontainerProperties() throws IOException {
        String properties = readProjectFile(DEV_PROPERTIES);

        // LoginCheckLogin.isBlock(ip, userName) only tracks the username when
        // login_lock=true; otherwise it falls back to the client IP, so failing
        // `locktest` would lock the browser's address (carlosdoc included) instead
        // of the account the Unlock Account screen is meant to show.
        assertThat(properties.lines().map(String::trim))
                .as("the devcontainer must enable username-based lockout for the locktest flow")
                .contains("login_lock=true");
    }

    @Test
    @DisplayName("should cover the data-backed Administration screens that are empty in the base dump")
    void shouldCoverAdministrationScreens_whenBaseDumpIsEmpty() throws IOException {
        String seed = readProjectFile(ADMIN_SEED);

        assertThat(seed).contains(
                "'999996'",
                "INSERT INTO cssStyles",
                "INSERT INTO incomingLabRules",
                "INSERT INTO incomingLabRulesType",
                "INSERT INTO eform_groups",
                "INSERT INTO eform_data",
                "INSERT INTO billingreferral",
                "INSERT INTO default_issue",
                "INSERT INTO reportTemplates",
                "INSERT INTO reportByExamplesFavorite",
                "INSERT INTO demographicQueryFavourites",
                "INSERT INTO appointmentType",
                "INSERT INTO PreventionsLotNrs");
    }

    @Test
    @DisplayName("should seed current and deleted patient-independent eForms")
    void shouldSeedPatientIndependentEforms_forBothAdministrationViews() throws IOException {
        String seed = readProjectFile(ADMIN_SEED);

        // EFormUtil.getEFormGroups() reports members as count(*)-1, so the group needs
        // the same fid=0 marker row AddGroup2Action writes or it renders a count of 0.
        assertThat(seed)
                .as("eForm groups need the fid=0 marker row the Administration UI creates")
                .contains("SELECT 0, 'Local Admin Tests'");

        assertThat(seed).contains(
                "'Local Test - Independent Checklist'",
                "'Local Test - Shared Operations Note'",
                "patient_independent");

        // Pin the status column, not just the subject. The eform_data column order
        // is (subject, demographic_no, status), so the trailing pair below is
        // "0, 1" for a current instance and "0, 0" for a deleted one. Asserting
        // only the subject would let a fixture silently flip between the
        // Administration "current" and "deleted" views.
        assertThat(seed)
                .as("current patient-independent eForms must keep status 1")
                .contains(
                        "'Local Test - Monthly Safety Review', 0, 1,",
                        "'Local Test - Quarterly Operations Review', 0, 1,");
        assertThat(seed)
                .as("the archived fixture must keep status 0 so the Deleted eForms view stays populated")
                .contains("'Local Test - Archived Safety Draft', 0, 0,");
    }

    @Test
    @DisplayName("should keep local admin fixtures repeatable and visibly synthetic")
    void shouldKeepFixtures_repeatableAndSynthetic() throws IOException {
        String seed = readProjectFile(ADMIN_SEED);

        assertThat(seed)
                .contains("WHERE NOT EXISTS")
                .contains("Local Test -")
                .contains("LOCAL-FLU-2026-A")
                .contains("LOCAL-COVID-2026-B")
                .contains("LOCAL-DTAP-ARCHIVED")
                .doesNotContain("INSERT INTO demographic ");
    }

    private static String readProjectFile(Path relativePath) throws IOException {
        return Files.readString(resolveProjectPath(relativePath), StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        int checkedParents = 0;
        while (current != null && checkedParents < MAX_PARENT_SEARCH_DEPTH) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
            checkedParents++;
        }
        throw new IllegalStateException("Unable to locate " + relativePath
                + " within " + MAX_PARENT_SEARCH_DEPTH + " parent directories from "
                + System.getProperty("user.dir"));
    }
}
