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

import javax.sql.DataSource;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.InitializingBean;

/**
 * Application-boot schema-version gate backed by Flyway.
 *
 * <p>CARLOS manages its MariaDB/MySQL schema with Flyway migrations (a consolidated
 * {@code V1} baseline plus sequential {@code V1.0.N} deltas). In production the migrations are
 * applied by an explicit, operator-gated step ({@code carlos-ctl db migrate}, run after a
 * pre-migration backup) — <strong>never</strong> silently on application boot, because a
 * multi-node deployment would otherwise have every instance racing to migrate a PHI schema on a
 * pod restart, and regulators expect schema changes to be a reviewed, logged operation.</p>
 *
 * <p>This bean therefore runs at context init in one of three modes, selected by the
 * {@code carlos.flyway.onBoot} property:</p>
 * <ul>
 *   <li>{@code off} (default) — do nothing. Schema is assumed to be managed entirely out of band
 *       (an operator-run {@code migrate}, or a pre-Flyway datadir). This preserves the historical
 *       behaviour where the app performs no schema management.</li>
 *   <li>{@code validate} — verify the database matches the schema the deployed WAR expects and
 *       <strong>fail fast</strong> otherwise: applied-migration checksums must match the shipped
 *       files AND no shipped migration may still be pending (a database that is <em>behind</em> the
 *       WAR aborts boot; one that is <em>ahead</em> — carrying applied migrations the WAR does not
 *       ship — fails Flyway validation too, which protects against running an older WAR on a newer
 *       schema). This is the recommended production posture once the baseline has been adopted:
 *       the app refuses to start against a schema it was not built for, rather than failing later
 *       with obscure column-not-found errors.</li>
 *   <li>{@code migrate} — apply pending migrations on boot. Intended for a disposable,
 *       single-node dev database that starts <strong>empty</strong> (or already carries a
 *       {@code flyway_schema_history}); do not use in multi-node production. This gate deliberately
 *       does NOT set {@code baselineOnMigrate}: a non-empty pre-Flyway datadir must be adopted by
 *       the explicit operator step ({@code carlos-ctl db baseline --version=1.0.2}, the full
 *       genesis), never auto-stamped on boot — an automatic baseline at the wrong version would
 *       make Flyway re-run the province files ({@code V1.0.1} DROPs and recreates province tables:
 *       data loss). Such a datadir fails loud here instead.</li>
 * </ul>
 *
 * <p>The bean reads migrations from the classpath location(s) in {@code carlos.flyway.locations}
 * (the WAR ships them at {@code db/migration}). It is deliberately import-light and holds no
 * Hibernate/JPA dependency so it can run before the {@code EntityManagerFactory} is touched.</p>
 */
public class FlywaySchemaValidator implements InitializingBean {

    private static final Logger logger = LogManager.getLogger(FlywaySchemaValidator.class);

    /** Do-nothing default: schema managed out of band. */
    static final String MODE_OFF = "off";
    /** Verify-and-fail-fast: refuse to start if the schema is behind the deployed WAR. */
    static final String MODE_VALIDATE = "validate";
    /** Apply pending migrations on boot (dev/single-node only). */
    static final String MODE_MIGRATE = "migrate";

    private final DataSource dataSource;
    private final String mode;
    private final String[] locations;

    /**
     * @param dataSource the application {@code DataSource} (same pool the app uses); Flyway opens
     *                   its own short-lived connections from it and never holds one.
     * @param mode       one of {@code off} / {@code validate} / {@code migrate}. Blank/absent means
     *                   {@code off} (a rendered carlos.properties that predates the key still
     *                   boots). Any OTHER value aborts boot: a mistyped {@code validate} silently
     *                   downgrading to {@code off} would disable the schema gate without anyone
     *                   noticing, which is worse than failing loud on the typo.
     * @param locations  comma-separated Flyway locations (e.g. {@code classpath:db/migration/common,
     *                   classpath:db/migration/on}); blank is only legal in {@code off} mode.
     */
    public FlywaySchemaValidator(DataSource dataSource, String mode, String locations) {
        this.dataSource = dataSource;
        this.mode = normalizeMode(mode);
        this.locations = splitLocations(locations);
    }

    // IMPROPER_UNICODE flags any case folding regardless of Locale; this is an intended
    // case-insensitive comparison of a three-value ASCII config token, not a trust decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Intended case-insensitive compare of an ASCII config token (off|validate|migrate)")
    private static String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODE_OFF;
        }
        String m = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (MODE_OFF.equals(m) || MODE_VALIDATE.equals(m) || MODE_MIGRATE.equals(m)) {
            return m;
        }
        throw new IllegalArgumentException(
                "carlos.flyway.onBoot must be off|validate|migrate but was '" + raw
                        + "' — refusing to guess (a typo must not silently disable the schema gate)");
    }

    private static String[] splitLocations(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void afterPropertiesSet() {
        if (MODE_OFF.equals(mode)) {
            logger.info("Flyway boot gate disabled (carlos.flyway.onBoot=off); schema managed out of band");
            return;
        }
        if (locations.length == 0) {
            // An empty location set in validate/migrate mode is a broken configuration, not a
            // reason to silently skip the gate the operator explicitly turned on.
            throw new IllegalStateException("carlos.flyway.onBoot=" + mode
                    + " requires carlos.flyway.locations to be set — refusing to boot with the "
                    + "schema gate enabled but nothing to validate against");
        }
        // Deliberately NO baselineOnMigrate: auto-stamping a non-empty pre-Flyway datadir on boot
        // is a data-loss trap (stamped at "1", the next migrate re-runs V1.0.1 which DROPs and
        // recreates province tables). Conversions must use the explicit, operator-gated
        // `carlos-ctl db baseline --version=1.0.2` (the full genesis) instead; an unadopted
        // non-empty schema fails loud here.
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load();

        if (MODE_MIGRATE.equals(mode)) {
            MigrateResult result = flyway.migrate();
            logger.info("Flyway boot migrate complete: {} migration(s) applied, schema now at {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
            return;
        }

        // MODE_VALIDATE: fail fast when the running schema does not match the deployed WAR.
        // validate() covers checksum drift and applied-but-not-shipped migrations; the explicit
        // pending check below makes "database is BEHIND the WAR" a hard failure too — Flyway's
        // treatment of pending migrations during validate has varied across versions, so the
        // contract is enforced here rather than assumed.
        flyway.validate();
        // Resolve migration state once and reuse it for both the pending check and the current
        // version log — flyway.info() rescans the migration set + history table on each call.
        MigrationInfoService info = flyway.info();
        MigrationInfo[] pending = info.pending();
        if (pending.length > 0) {
            StringBuilder versions = new StringBuilder();
            for (MigrationInfo p : pending) {
                if (versions.length() > 0) {
                    versions.append(", ");
                }
                versions.append(p.getVersion() != null ? p.getVersion().toString() : p.getDescription());
            }
            throw new IllegalStateException("database schema is BEHIND the deployed WAR: "
                    + pending.length + " pending migration(s) [" + versions
                    + "] — run `carlos-ctl db migrate` (after a backup) before starting the app");
        }
        MigrationInfo current = info.current();
        logger.info("Flyway schema validation passed; database at version {}",
                current != null ? current.getVersion() : "(none)");
    }
}
