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

package io.github.carlos_emr.carlos.app.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the invariant that every security object CARLOS gates on is actually
 * reachable in a freshly migrated database.
 *
 * <p><b>Why this exists.</b> {@code _admin.edocdelete} was created only by the
 * frozen legacy script {@code database/mysql/updates/update-2008-10-20.sql} and
 * appeared nowhere in the Flyway migration set. On a fresh install the row simply
 * did not exist, so {@code hasPrivilege(..., "_admin.edocdelete", "w", ...)}
 * returned false for every user forever. In {@code DocumentUndelete2Action} that
 * silently demoted administrators to the creator-only undelete branch, and it
 * would have made outbound email archives permanently unretirable.</p>
 *
 * <p><b>Why the existing tests could not catch it.</b> Unit tests stub
 * {@code SecurityInfoManager.hasPrivilege} directly, so they assert what the code
 * does <em>given</em> a privilege and are blind to whether that privilege can ever
 * be held; {@code DocumentUndelete2ActionTest} has a
 * {@code shouldAllowAdmin_toUndeleteAny} case that passed throughout. Integration
 * tests are no better: H2 builds its schema from the entities via
 * {@code hbm2ddl.auto=create}, so the seed tables come up empty. Only the
 * migration SQL knows the truth, so this test reads the migration SQL.</p>
 *
 * <p><b>Two distinct failure modes,</b> both of which leave a gate permanently
 * shut: the object name is never inserted into {@code secObjectName}, or it is
 * inserted but no {@code secObjPrivilege} row ever grants it to a role. Checking
 * grants alone covers both, since an ungranted name is unreachable either way.</p>
 *
 * <p><b>When this test fails on code you just wrote:</b> seed the object name into
 * {@code secObjectName} and grant it to the appropriate role in the migration that
 * introduces your feature. Do NOT add it to the allowlist below — that list is for
 * pre-existing debt only.</p>
 *
 * @since 2026-08-17
 */
@DisplayName("Security object seed contract")
@Tag("unit")
@Tag("security")
class SecurityObjectSeedContractUnitTest {

    private static final Path MIGRATIONS = Path.of("database/mysql/migration");
    private static final Path MAIN_JAVA = Path.of("src/main/java");

    /** Sec object literal appearing shortly after a hasPrivilege( call. */
    private static final Pattern PRIVILEGE_CALL =
            Pattern.compile("hasPrivilege\\s*\\([^;{}]{0,400}?\"(_[A-Za-z0-9._]+)\"", Pattern.DOTALL);

    /** Any single-quoted sec-object-shaped literal inside a seed statement. */
    private static final Pattern SEED_OBJECT = Pattern.compile("'(_[A-Za-z0-9._]+)'");

    /**
     * Pre-existing gaps, captured when this contract was introduced. Every entry is
     * a security object that production code gates on but that a fresh database can
     * never grant, so the guarded feature is unreachable out of the box.
     *
     * <p>These are NOT approved: this list is debt the contract refuses to let grow.
     * Do not extend it to silence a new failure — fix the migration instead.
     * Shrinking it is always welcome; each removal is one feature that starts
     * working on a fresh install.</p>
     */
    private static final Set<String> KNOWN_UNREACHABLE = Set.of(
            // Absent from secObjectName entirely.
            "_admin.lab",
            "_admin.torontoRfq",
            "_appointment.UpdatedAfterDate",
            "_newCasemgmt.eforms",
            "_pmm_management",
            // Present in secObjectName but granted to no role.
            "_admin.backup",
            "_admin.encounter",
            "_admin.messenger",
            "_admin.resource",
            "_admin.userAdmin",
            "_dashboardChgUser",
            "_dashboardDisplay",
            "_dashboardDrilldown",
            "_dashboardManager",
            "_team_access_privacy",
            "_team_billing_only");

    @ParameterizedTest(name = "{0} province schema")
    @ValueSource(strings = {"on", "bc"})
    @DisplayName("should grant every security object the code gates on")
    void shouldGrantEverySecurityObject_forProvinceSchema(String province) {
        Set<String> referenced = securityObjectsReferencedInCode();
        // Sanity-check the scanner itself: a silently broken regex would make this
        // test vacuously green, the one outcome worse than a red build.
        assertThat(referenced)
                .as("hasPrivilege scanner found too few security objects - the scan is broken, not the code")
                .hasSizeGreaterThan(50);

        Set<String> granted = grantedObjects(province);
        assertThat(granted)
                .as("no secObjPrivilege grants parsed from the %s migration set", province)
                .isNotEmpty();

        Set<String> unreachable = new TreeSet<>(referenced);
        unreachable.removeAll(granted);
        unreachable.removeAll(KNOWN_UNREACHABLE);

        assertThat(unreachable)
                .as("Security objects gated on in code but never granted to any role in the %s "
                        + "migration set. hasPrivilege() returns false for every user, so the "
                        + "guarded feature is unreachable on a fresh install. Seed the object into "
                        + "secObjectName and grant it in the migration that introduces the feature.",
                        province)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} province schema")
    @ValueSource(strings = {"on", "bc"})
    @DisplayName("should keep the known-unreachable allowlist honest")
    void shouldKeepAllowlistHonest_forProvinceSchema(String province) {
        Set<String> granted = grantedObjects(province);
        Set<String> stale = new TreeSet<>(KNOWN_UNREACHABLE);
        stale.retainAll(granted);

        assertThat(stale)
                .as("Listed as known-unreachable, but the %s migration set now grants them. "
                        + "Delete them from KNOWN_UNREACHABLE so the list keeps shrinking.", province)
                .isEmpty();
    }

    /** Objects granted to at least one role by the common + province migration set. */
    private Set<String> grantedObjects(String province) {
        Set<String> granted = new LinkedHashSet<>();
        for (Path file : migrationFiles(province)) {
            // Split on statement terminators so a secObjPrivilege insert cannot absorb
            // object names belonging to a neighbouring statement.
            for (String statement : read(file).split(";")) {
                if (!statement.contains("secObjPrivilege") || !statement.contains("INSERT")) {
                    continue;
                }
                Matcher matcher = SEED_OBJECT.matcher(statement);
                while (matcher.find()) {
                    granted.add(matcher.group(1));
                }
            }
        }
        return granted;
    }

    private List<Path> migrationFiles(String province) {
        try (Stream<Path> common = Files.walk(MIGRATIONS.resolve("common"));
             Stream<Path> provincial = Files.walk(MIGRATIONS.resolve(province))) {
            return Stream.concat(common, provincial)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the migration set", e);
        }
    }

    private Set<String> securityObjectsReferencedInCode() {
        Set<String> referenced = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        String content = read(path);
                        if (!content.contains("hasPrivilege")) {
                            return;
                        }
                        Matcher matcher = PRIVILEGE_CALL.matcher(content);
                        while (matcher.find()) {
                            referenced.add(matcher.group(1));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot scan the main source tree", e);
        }
        return referenced;
    }

    /**
     * Reads a file as UTF-8. A handful of legacy sources and SQL dumps carry
     * non-UTF-8 bytes; those are skipped rather than failing the contract, since a
     * decoding quirk in an unrelated file is not a security-seed defect.
     */
    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
