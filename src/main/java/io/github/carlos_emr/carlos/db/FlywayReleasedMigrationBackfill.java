/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.RepairResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Reconcile the published release import chain with an already migrated develop database. Only the
 * exact released scripts below may arrive below develop's 1.0.81 high-water mark. Flyway still
 * executes and records each script normally and validates every applied checksum. No history is
 * stamped, repaired, ignored or deleted by this policy.
 */
public final class FlywayReleasedMigrationBackfill {
    private static final MigrationVersion DEVELOP = MigrationVersion.fromVersion("1.0.81");
    // Flyway CRC32 values of the frozen release SQL, without line endings, as Flyway computes them.
    private static final Map<String, Integer> RELEASED =
            Map.ofEntries(
                    Map.entry("V1.0.59.29__preserve_oscar19_text_capacity.sql", -2109751502),
                    Map.entry("V1.0.59.31__restore_bcar2007_urine_culture_text.sql", 885028202),
                    Map.entry("V1.0.59.10__cds_laboratory_results.sql", -1946124121),
                    Map.entry("V1.0.59.11__cds_contact_care_capacity.sql", 28357732),
                    Map.entry("V1.0.59.12__cds_preflight_identity.sql", 267914620),
                    Map.entry("V1.0.59.13__cds_import_review_history.sql", 977261540),
                    Map.entry("V1.0.59.14__cds_native_preparation.sql", 21217301),
                    Map.entry("V1.0.59.15__cds_file_verification_epoch.sql", 1831103711),
                    Map.entry("V1.0.59.16__cds_provider_lookup_indexes.sql", -101866357),
                    Map.entry("V1.0.59.17__cds_review_source_provenance.sql", 573505228),
                    Map.entry("V1.0.59.18__cds_medication_projection_safety.sql", 1566200217),
                    Map.entry("V1.0.59.19__cds_held_patient_lookup.sql", 867789613),
                    Map.entry("V1.0.59.20__cds_identity_authority.sql", 40708426),
                    Map.entry(
                            "V1.0.59.21__cds_note_unknown_observation_and_import_permission.sql",
                            1444351196),
                    Map.entry("V1.0.59.22__cds_alphanumeric_prevention_creator.sql", -2044905017),
                    Map.entry("V1.0.59.23__cds_report_content_precision.sql", 349748923),
                    Map.entry("V1.0.59.24__cds_durable_resume_intent.sql", -66098505),
                    Map.entry("V1.0.59.25__cds_review_kind_capacity.sql", 1127325405),
                    Map.entry("V1.0.59.26__cds_measurements_deleted_capacity.sql", 411093490),
                    Map.entry("V1.0.59.27__preserve_oscar19_text_capacity.sql", -1665814421),
                    Map.entry("V1.0.59.30__one_live_consent_per_type.sql", -1417976536),
                    Map.entry("V1.0.59.32__preserve_native_archive_capacity.sql", 1300518019),
                    Map.entry("V1.0.59.33__audit_imported_billing_filenames.sql", -1103171947),
                    Map.entry("V1.0.59.8.1__cds_import_ledger.sql", 1714557077),
                    Map.entry("V1.0.59.8__widen_messagetbl_pdfattachment.sql", -758945161),
                    Map.entry("V1.0.59.9__cds_upload_sessions.sql", -1023529232),
                    Map.entry("V1.0.59.28__preserve_oscar19_text_capacity.sql", 1643827960));

    private FlywayReleasedMigrationBackfill() {}

    public static Flyway load(FluentConfiguration configuration) {
        var callbacks = new ArrayList<>(Arrays.asList(configuration.getCallbacks()));
        callbacks.removeIf(
                callback ->
                        callback instanceof FlywayPropertyCapacityPreservation
                                || callback instanceof FlywayConsentAuditPreparation);
        var strictCallbacks = new ArrayList<>(callbacks);
        strictCallbacks.add(new FlywayPropertyCapacityPreservation(false));
        configuration.callbacks(strictCallbacks.toArray(Callback[]::new));
        Flyway strict = configuration.outOfOrder(false).load();
        var info = strict.info();
        var ignored =
                Arrays.stream(info.all())
                        .filter(migration -> migration.getState() == MigrationState.IGNORED)
                        .toList();
        if (ignored.isEmpty()) return strict;
        var current = info.current();
        if (current == null
                || current.getVersion() == null
                || current.getVersion().compareTo(DEVELOP) < 0)
            throw new IllegalStateException("FLYWAY_RELEASED_BACKFILL_HISTORY_UNREVIEWED");
        for (var migration : ignored) {
            Integer checksum = RELEASED.get(migration.getScript());
            if (checksum == null || !Objects.equals(checksum, migration.getChecksum()))
                throw new IllegalStateException("FLYWAY_RELEASED_BACKFILL_SCRIPT_UNREVIEWED");
        }
        // This instance can only apply the exact reviewed historical arrivals above. Applied
        // failures, checksum drift, missing migrations and future history remain fatal to Flyway.
        if (ignored.stream()
                .anyMatch(
                        migration ->
                                FlywayConsentAuditPreparation.SCRIPT.equals(
                                        migration.getScript()))) {
            callbacks.add(new FlywayConsentAuditPreparation());
        }
        callbacks.add(
                new FlywayPropertyCapacityPreservation(
                        ignored.stream()
                                .anyMatch(
                                        migration ->
                                                FlywayPropertyCapacityPreservation.SCRIPT.equals(
                                                        migration.getScript()))));
        configuration.callbacks(callbacks.toArray(Callback[]::new));
        return configuration.outOfOrder(true).load();
    }

    public static MigrateResult migrate(Flyway flyway) {
        return FlywayMigrationOperationLock.execute(
                flyway,
                connection -> {
                    return FlywayPropertyCapacityPreservation.duringMigrate(flyway::migrate);
                });
    }

    public static RepairResult repair(Flyway flyway) {
        return FlywayMigrationOperationLock.execute(
                flyway,
                connection -> {
                    FlywayPropertyCapacityPreservation.verifyPendingRecovery(connection);
                    return flyway.repair();
                });
    }
}
