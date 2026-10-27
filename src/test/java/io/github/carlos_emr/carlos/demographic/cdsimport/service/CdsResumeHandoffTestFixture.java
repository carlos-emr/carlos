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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionInitializer;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportLedgerMariaDbIntegrationTest.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Staging-only dry-run crash proof using the real MariaDB ledger and physical owners.
 * Source-validation/report/auth adapters are fixture stubs; no clinical import, native receipt or
 * report-delivery success is claimed. The actual parser/auth/report contracts run separately.
 */
final class CdsResumeHandoffTestFixture {
    private CdsResumeHandoffTestFixture() { }

    static void unsealed(Properties jdbc) throws Exception {
        String url = jdbc.getProperty("jdbcUrl", "").replace("jdbc:mariadb:", "jdbc:mysql:");
        if (!url.matches("jdbc:mysql://[^/]+/cds_ledger_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Dedicated disposable ledger schema required");
        }
        Properties policy = new Properties();
        policy.setProperty("cds.import.enabled", "true");
        policy.setProperty("cds.import.maintenanceMode", "true");
        var config = new CdsImportConfigurationService(policy);
        var physical = new OscarTrackingBasicDataSource();
        physical.setDriverClassName("com.mysql.cj.jdbc.Driver"); physical.setUrl(url);
        physical.setUsername(jdbc.getProperty("jdbcUser")); physical.setPassword(jdbc.getProperty("jdbcPassword"));
        physical.setDefaultAutoCommit(true);
        try (physical) {
            new CdsJdbcAdmissionInitializer(physical, true, "validate").initialize();
            var environment = new Environment(config, new CdsImportCoordinatorService(physical),
                    new CdsImportMaintenanceService(physical, config), new CdsImportTargetSafetyService(config));
            recover(environment, false);
            recover(environment, true);
        }
    }

    private static void recover(Environment environment, boolean laterCancel) throws Exception {
        long nativeBefore = countNative();
        String source = "handoff-" + UUID.randomUUID();
        var actor = new CdsImportActorDto("fixture", "999998", "999998", null, null, null);
        var command = new CdsImportCommand(Path.of("/opaque/handoff.xml"), UUID.randomUUID().toString(),
                "999998", null, null, "fixture", "omd-5.2", false, true, false, 0, null, false);
        String options = CdsImportJobService.optionsDigest(command);
        String rules = CdsImportJobService.rulesetDigest(command, environment.config());
        String id = ledger.accept(new CdsImportJobCommand(command.submissionKey(), actor.operator(), command.providerNo(),
                command.sourceNamespace(), command.sourceProfile(), A, options, rules, "/opaque/handoff", true)).id();
        long oldEpoch = ledger.claimPlanning(id);
        ledger.planPatient(id, oldEpoch, "patient", source, "primary", A);
        // Accept a real operator control while the previous preflight is still alive. Its later
        // stopped report must not hide that control from a new process's default dispatcher.
        ledger.control(id, actor, "RESUME_REQUESTED", () -> ledger.requestResumeParticipating(id));
        ledger.endPreflight(id, oldEpoch, "FAILED_BEFORE_WRITE", "SYNTHETIC_PREFLIGHT_STOP");
        // Only the report payload is a fixture placeholder; its epoch/generation selection CAS is real.
        CdsImportLedgerMariaDbIntegrationTest.reports.select(id, oldEpoch, ledger.status(id).reportGeneration(),
                A, "/opaque/stopped-report-" + UUID.randomUUID(), A);
        ledger.operational(id, oldEpoch, null, "COMPLETE", "SYNTHETIC_PREFLIGHT_STOP");
        ledger.release(id, oldEpoch);
        check("NOT_STARTED".equals(ledger.patientStatus(id, "patient").state()), "old unsealed patient is explicitly not started");
        check(ledger.pendingResumeEvent(id) > 0, "stopped report retains audited clinical Resume authority");
        check(ledger.pending("", 200).stream().anyMatch(value -> id.equals(value.id())), "stopped delivered source is rediscovered from durable intent");

        long preparationEpoch;
        try (var coordinator = environment.coordinators().acquire(); var owner = environment.maintenance().acquire()) {
            environment.safety().verify(owner);
            preparationEpoch = ledger.claimPreparation(id, coordinator, owner, true);
            check("FAILED_BEFORE_WRITE".equals(ledger.status(id).state())
                    && Boolean.TRUE.equals(ledger.summary(id).get("resumeRequested")) && ledger.pendingResumeEvent(id) > 0,
                    "preparation commit keeps stopped recovery discoverable until an atomic phase handoff");
        }
        // Process-loss boundary: close real physical resources, but do not call ledger.release.
        check(ledger.ownsAdmission(id, preparationEpoch), "dead preparation retains fenced admission for its successor");

        var jobs = mock(CdsImportJobService.class);
        var planner = mock(CdsImportPlanningService.class);
        var nativeRecords = mock(CdsImportNativeService.class);
        var reports = mock(CdsImportResultService.class);
        var snapshot = new CdsSnapshotDto(Path.of("/opaque/snapshot"), Path.of("/opaque/snapshot/files.jsonl"), A, 1, 1, 1);
        var document = new CdsImportJobDocumentDto(command, actor, snapshot, null, Path.of("/opaque"), rules);
        when(jobs.load(id)).thenReturn(document); when(jobs.snapshot(eq(id), eq(document), any())).thenReturn(snapshot);
        var interruptedLedger = spy(ledger);
        AssertionError processLoss = new AssertionError("Synthetic process loss after committed PLANNING");
        doAnswer(call -> {
            ledger.claimPlanning(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            throw processLoss;
        }).when(interruptedLedger).claimPlanning(eq(id), any(CdsImportCoordinatorService.Owner.class), any(CdsImportMaintenanceService.Owner.class));
        var interrupted = engine(environment, interruptedLedger, jobs, planner, nativeRecords, reports);
        try {
            interrupted.execute(id);
            throw new AssertionError("Missing synthetic process-loss boundary");
        } catch (AssertionError failed) { check(failed == processLoss, "only the preallocated post-commit fault stops this process"); }
        long planningEpoch = ledger.status(id).ownerEpoch();
        check(planningEpoch > preparationEpoch && "PLANNING".equals(ledger.status(id).state()), "phase ownership and durable PLANNING committed together");
        check("PENDING".equals(ledger.summary(id).get("reportState"))
                && "PENDING".equals(ledger.summary(id).get("cleanupState")), "old stopped-report delivery cannot certify the new planning phase");
        check(!Boolean.TRUE.equals(ledger.summary(id).get("resumeRequested")) && ledger.pendingResumeEvent(id) == 0,
                "phase commit consumes only its accepted Resume intent");
        check(Boolean.FALSE.equals(ledger.summary(id).get("discoveryComplete"))
                && "NOT_STARTED".equals(ledger.patientStatus(id, "patient").state()), "crash precedes source discovery and preflight restoration");
        check(ledger.pending("", 200).stream().anyMatch(value -> id.equals(value.id())), "PLANNING remains discoverable after the intent bit is consumed");
        verify(jobs, never()).snapshot(anyString(), any(), any()); verifyNoInteractions(planner, nativeRecords);

        AtomicInteger plans = new AtomicInteger();
        doAnswer(call -> {
            plans.incrementAndGet(); long epoch = call.getArgument(1);
            check("PLANNED".equals(ledger.patientStatus(id, "patient").state()), "fresh process repeats idempotent restoration of NOT_STARTED before planning");
            // The source-staging stub represents a complete validated source/manifest. Only this
            // administrative proof permits retirement of the old preflight-stop diagnostic.
            String unit = CdsImportKeys.hash(source, "demographics");
            ledger.planUnit(id, epoch, "patient", unit, "Demographics", 1, A, "/opaque/handoff-unit");
            ledger.discovery(id, epoch, 1, 1, 1);
            ledger.resolveRevalidatedPreflightStops(id, epoch, C);
            ledger.seal(id, epoch, C, 1, 1);
            return null;
        }).when(planner).plan(eq(id), anyLong(), eq(document), eq(snapshot), any());
        var restarted = engine(environment, ledger, jobs, planner, nativeRecords, reports);
        if (laterCancel) {
            ledger.control(id, actor, "CANCEL_REQUESTED", () -> ledger.cancelParticipating(id));
            long cancelledEpoch = ledger.status(id).ownerEpoch();
            restarted.execute(id);
            check(ledger.status(id).cancelRequested() && ledger.status(id).ownerEpoch() == cancelledEpoch,
                    "later cancellation wins without advancing the old phase epoch");
            check(plans.get() == 0 && ledger.pendingResumeEvent(id) == 0, "default restart cannot create new planning consent after Cancel");
            verify(jobs, never()).snapshot(anyString(), any(), any());
        } else {
            var result = restarted.execute(id);
            check("DRY_RUN_COMPLETE".equals(result.state()) && plans.get() == 1, "fresh default engine restages and finishes dry-run without another Resume");
            check(Boolean.TRUE.equals(ledger.summary(id).get("discoveryComplete")) && ledger.isSealed(id), "restaged expected work and source inventory are sealed");
            check(((Number) ledger.summary(id).get("committedUnits")).longValue() == 0, "dry-run handoff reports zero imported units");
        }
        check(countNative() == nativeBefore && ledger.patientStatus(id, "patient").targetDemographicNo() == null
                && !ledger.patientStatus(id, "patient").held(), "both unsealed restart paths leave all native data and target admission unchanged");
        check(sql.queryForObject("SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND event_type='RESUME_REQUESTED'", Long.class, id) == 1
                && sql.queryForObject("SELECT COUNT(*) FROM cds_import_event WHERE run_id=? AND event_type='RESUME_INTENT_APPLIED'", Long.class, id) == 1,
                "default restart creates no new operator consent and consumes the original intent exactly once");
        verifyNoInteractions(nativeRecords);
    }

    private static CdsImportExecutionService engine(Environment environment, CdsImportLedgerService records,
            CdsImportJobService jobs, CdsImportPlanningService planning, CdsImportNativeService nativeRecords,
            CdsImportResultService reports) {
        return new CdsImportExecutionService(records, units, jobs, planning, nativeRecords, mock(CdsImportAccessService.class),
                environment.config(), environment.maintenance(), reports, environment.coordinators(),
                mock(CdsImportFileVerificationService.class), environment.safety(), mock(CdsImportFailureEvidenceService.class), new CdsFieldParser());
    }
    private record Environment(CdsImportConfigurationService config, CdsImportCoordinatorService coordinators,
            CdsImportMaintenanceService maintenance, CdsImportTargetSafetyService safety) { }
}
