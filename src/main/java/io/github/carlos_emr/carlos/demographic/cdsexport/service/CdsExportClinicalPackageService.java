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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.commn.dto.CdsExportLocalSourceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportSupportingFilesValidator;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Cohort packaging orchestration over the shared patient pipeline. Selection EOF, same-capture
 * file binding, actual XML counts, supporting-file read-back, sorted membership and complete
 * ZIP validation must all succeed. This private byte/package capability does not qualify a
 * whole-chart source catalogue, prove clinical semantics, encrypt delivery or publish READY.
 */
@Service
public class CdsExportClinicalPackageService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ACCESS_CHANGED = "CDS_EXPORT_ACCESS_CHANGED";
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(CdsExportClinicalPackageService.class);

    public record Limits(
            long patients,
            long members,
            long indexBytes,
            long metadataBytes,
            long supportingBytes,
            long zipBytes) {
        public Limits {
            if (patients < 1
                    || patients > 2_000_000
                    || members < 1
                    || members > 2_000_000
                    || indexBytes < 12
                    || indexBytes > (64L << 30)
                    || metadataBytes < 1
                    || metadataBytes > (4L << 30)
                    || supportingBytes < 1
                    || supportingBytes > (64L << 30)
                    || zipBytes < 1
                    || zipBytes > (64L << 30))
                throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_PACKAGE_LIMIT_INVALID");
        }
    }

    private record PatientFile(
            CdsExportPatientDocumentService.Document document,
            CdsExportPatientFileDto naming,
            String relative,
            List<CdsExportNoteAccessDto> notes) {
        private PatientFile {
            notes = List.copyOf(notes);
        }
    }

    private final CdsExportStagingFactory stores;
    private final CdsExportCohortService cohorts;
    private final CdsExportLedgerService ledger;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportRetainedPayloadService payloads;
    private final CdsExportPatientDocumentService documents;
    private final CdsExportPatientFileBindingService naming;
    private final CdsExportPackageInventoryService inventories;
    private final CdsExportDisclosurePolicyService disclosure;
    private final CdsExportAuditPersister audit;

    public CdsExportClinicalPackageService(
            CdsExportStagingFactory stores,
            CdsExportCohortService cohorts,
            CdsExportLedgerService ledger,
            CdsExportNativeCaptureService captures,
            CdsExportRetainedPayloadService payloads,
            CdsExportPatientDocumentService documents,
            CdsExportPatientFileBindingService naming,
            CdsExportPackageInventoryService inventories,
            CdsExportDisclosurePolicyService disclosure,
            CdsExportAuditPersister audit) {
        this.stores = Objects.requireNonNull(stores);
        this.cohorts = Objects.requireNonNull(cohorts);
        this.ledger = Objects.requireNonNull(ledger);
        this.captures = Objects.requireNonNull(captures);
        this.payloads = Objects.requireNonNull(payloads);
        this.documents = Objects.requireNonNull(documents);
        this.naming = Objects.requireNonNull(naming);
        this.inventories = Objects.requireNonNull(inventories);
        this.disclosure = Objects.requireNonNull(disclosure);
        this.audit = Objects.requireNonNull(audit);
    }

    /** Starts with a committed cohort in CAPTURING and returns only a private validated ZIP in PACKAGING. */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public Prepared compose(
            CdsExportLeaseDto lease,
            String applicationVersion,
            ZoneId clinicalZone,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportXmlLimitsDto xmlLimits,
            Limits limits,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(control);
        Objects.requireNonNull(clinicalZone);
        Objects.requireNonNull(limits);
        var initial = ledger.status(lease.runId());
        if (initial.state() != CdsExportState.CAPTURING
                || initial.totalPatients() == null
                || initial.totalPatients() < 1) invalid();
        if (initial.totalPatients() > limits.patients()
                || initial.totalPatients() > limits.members() - 2) limit();
        var cohort = ledger.cohort(lease);
        var policy = disclosure.resolveAccepted(lease.document(), Instant.now());
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var owned = new ArrayList<CdsExportStagedFileDto>();
        var files = new TreeMap<String, PatientFile>();
        var held = new TreeMap<Integer, CdsExportHeldPatientDto>();
        // Charts whose capture began (the export read them), and those already in the audit.
        var read = new java.util.LinkedHashSet<Integer>();
        var audited = new HashSet<Integer>();
        var physicians = new TreeMap<String, CdsExportPhysicianSummaryDto>();
        var billingNames = new HashMap<String, String>();
        var tables = captures.requiredTables(lease.document().command());
        long[] metadataBytes = {0};
        long patients = 0, expectedMembers = 2;
        boolean complete = false;
        Throwable primary = null;
        try {
            try (var cursor = cohorts.open(lease, control)) {
                CdsExportCohortService.Patient patient;
                while ((patient = cursor.next()) != null) {
                    control.checkpoint();
                    if (++patients > limits.patients()) limit();
                    if (!cohort.plaintextSha256().equals(patient.cohortSha256())) invalid();
                    // A patient whose chart this release cannot represent is held back and named,
                    // never silently omitted; infrastructure failures still stop the whole job.
                    int ownedMark = owned.size();
                    long metadataMark = metadataBytes[0];
                    Captured captured;
                    read.add(patient.patient());
                    try {
                        captured =
                                capturePatient(
                                        lease,
                                        patient,
                                        tables,
                                        bounds,
                                        applicationVersion,
                                        xmlLimits,
                                        limits,
                                        control,
                                        owned,
                                        metadataBytes);
                    } catch (CdsExportReviewException
                            | CdsExportMappingReviewException
                            | SecurityException refused) {
                        String code = heldCode(refused);
                        if (code == null) throw refused;
                        for (int i = owned.size() - 1; i >= ownedMark; i--)
                            store.discardUnpublished(owned.remove(i));
                        metadataBytes[0] = metadataMark;
                        held.put(patient.patient(), new CdsExportHeldPatientDto(patient.patient(), code));
                        // Safe code only; the patient number is recorded in the protected
                        // disclosure manifest and the encrypted ReadMe, not in the log.
                        LOG.warn("CDS_EXPORT_PATIENT_HELD run={} code={}", lease.runId(), code);
                        continue;
                    }
                    var source = captured.source();
                    var document = captured.document();
                    var name = captured.name();
                    expectedMembers =
                            Math.addExact(expectedMembers, 1L + document.attachments().size());
                    if (expectedMembers > limits.members()) limit();
                    var path = new CdsExportPathComposer().compose(name);
                    String relative = path.physicianFolder() + "/" + path.patientXmlFile();
                    var noteScope = source.capture().tables().get("casemgmt_note");
                    var notes =
                            noteScope == null
                                    ? List.<CdsExportNoteAccessDto>of()
                                    : noteScope.noteAccess();
                    metadataBytes[0] =
                            Math.addExact(
                                    metadataBytes[0],
                                    4096L + 8L * relative.length() + 512L * notes.size());
                    if (metadataBytes[0] > limits.metadataBytes()) limit();
                    if (files.putIfAbsent(
                                    relative, new PatientFile(document, name, relative, notes))
                            != null) ambiguous();
                    String priorName =
                            billingNames.putIfAbsent(
                                    name.physicianBillingNumber(), path.physicianFolder());
                    if (priorName != null && !priorName.equals(path.physicianFolder())) ambiguous();
                    var prior = physicians.get(path.physicianFolder());
                    physicians.put(
                            path.physicianFolder(),
                            new CdsExportPhysicianSummaryDto(
                                    name.physicianFirstName(),
                                    name.physicianLastName(),
                                    name.physicianBillingNumber(),
                                    prior == null ? 1 : Math.addExact(prior.patientFiles(), 1)));
                }
                if (!cursor.complete()
                        || patients != initial.totalPatients()
                        || patients != files.size() + held.size()) invalid();
            }
            auditReads(lease, held.keySet(), CdsExportAuditPersister.ReadOutcome.HELD, audited);
            // Every selected patient held back: there is no package to publish.
            if (files.isEmpty()) {
                var refusal = allHeld(held.values());
                if (refusal instanceof CdsExportMappingReviewException mapping) throw mapping;
                throw (RuntimeException) refusal;
            }
            var localSources = captures.unreviewedLocalSources();
            if (!localSources.isEmpty())
                // Table names from information_schema only; no values or patient identifiers.
                LOG.warn(
                        "CDS_EXPORT_UNREVIEWED_LOCAL_TABLES run={} tables={}",
                        lease.runId(),
                        localSources.stream().map(CdsExportLocalSourceDto::table).toList());
            ledger.phase(lease, CdsExportState.RECONCILING);
            authorizeCohort(lease, tables, files.values(), held.keySet(), control, patients, false, audited);
            policy = disclosure.resolveAccepted(lease.document(), Instant.now());
            var metadata =
                    new CdsExportPackageMetadataDto(
                            "CDSExport-" + lease.runId(),
                            policy.physicianGroup(),
                            policy.groupContactInformation(),
                            applicationVersion,
                            Instant.now(),
                            clinicalZone,
                            lease.document().command().profile(),
                            lease.document().command().categories(),
                            policy.auditAccessDescription(),
                            List.copyOf(held.values()),
                            localSources);
            var byPatient = new TreeMap<Integer, PatientFile>();
            for (var file : files.values())
                if (byPatient.putIfAbsent(file.document().patient(), file) != null
                        || held.containsKey(file.document().patient())) invalid();
            var disclosed = new TreeMap<Integer, Object>(byPatient);
            disclosed.putAll(held);
            long totalPatients = patients;
            var header =
                    new ScopeHeader(
                            "HEADER",
                            1,
                            lease.runId(),
                            lease.epoch(),
                            cohort.plaintextSha256(),
                            lease.document().compatibilityDigest(),
                            patients,
                            new TreeSet<>(tables));
            var authorization =
                    store.stage(
                            "DISCLOSURE_MANIFEST",
                            output -> {
                                line(output, header);
                                for (var entry : disclosed.values()) {
                                    control.checkpoint();
                                    line(output, scopeLine(entry));
                                }
                                line(output, new ScopeEnd("END", totalPatients));
                            });
            owned.add(authorization);
            try (var input = store.open(authorization)) {
                var lines = new CdsExportJsonLines(input, 1 << 20, control::checkpoint);
                if (!same(header, lines.next())) invalid();
                for (var entry : disclosed.values())
                    if (!same(scopeLine(entry), lines.next())) invalid();
                if (!same(new ScopeEnd("END", patients), lines.next()) || lines.next() != null)
                    invalid();
            }
            ledger.phase(lease, CdsExportState.SERIALIZING);
            var eventLog = new CdsExportStagedFileDto[1];
            var totals = new CdsExportSupportingFilesComposer.Totals[1];
            long[] supportingBytes = {0};
            var readMe =
                    store.stage(
                            "SUPPORTING_README",
                            readme -> {
                                eventLog[0] =
                                        store.stage(
                                                "SUPPORTING_EVENT_LOG",
                                                log ->
                                                        totals[0] =
                                                                new CdsExportSupportingFilesComposer()
                                                                        .compose(
                                                                                new LimitedOutput(
                                                                                        readme,
                                                                                        supportingBytes,
                                                                                        limits
                                                                                                .supportingBytes()),
                                                                                new LimitedOutput(
                                                                                        log,
                                                                                        supportingBytes,
                                                                                        limits
                                                                                                .supportingBytes()),
                                                                                metadata,
                                                                                physicians
                                                                                        .values()
                                                                                        .iterator(),
                                                                                summaries(
                                                                                                files
                                                                                                        .values())
                                                                                        .iterator(),
                                                                                control
                                                                                        ::checkpoint));
                                owned.add(eventLog[0]);
                            });
            owned.add(readMe);
            if (totals[0].patientFiles() != files.size()
                    || totals[0].physicians() != physicians.size()) invalid();
            ledger.phase(lease, CdsExportState.VALIDATING);
            CdsExportSupportingFilesValidator.Result supporting;
            try (var readme = store.open(readMe);
                    var log = store.open(eventLog[0])) {
                supporting =
                        new CdsExportSupportingFilesValidator()
                                .validate(
                                        readme,
                                        log,
                                        metadata,
                                        physicians.values().iterator(),
                                        files.values().stream()
                                                .map(
                                                        file ->
                                                                new CdsExportSupportingFilesValidator
                                                                        .Patient(
                                                                        metadata.exportRoot()
                                                                                + "/"
                                                                                + file.relative(),
                                                                        file.naming(),
                                                                        file.document()
                                                                                .validation()))
                                                .iterator(),
                                        Math.min(limits.patients(), 1_000_000),
                                        limits.patients(),
                                        limits.supportingBytes(),
                                        control::checkpoint);
            }
            if (!supporting.readMeSha256().equals(readMe.plaintextSha256())
                    || supporting.readMeBytes() != readMe.plaintextBytes()
                    || !supporting.eventLogSha256().equals(eventLog[0].plaintextSha256())
                    || supporting.eventLogBytes() != eventLog[0].plaintextBytes()
                    || supporting.patientFiles() != files.size()
                    || supporting.physicians() != physicians.size()) invalid();
            ledger.phase(lease, CdsExportState.PACKAGING);
            var members =
                    inventories.seal(
                            store,
                            limits.members(),
                            limits.indexBytes(),
                            control::checkpoint,
                            output -> {
                                output.accept(new CdsExportPackageMemberDto("ReadMe.txt", readMe));
                                output.accept(
                                        new CdsExportPackageMemberDto(
                                                "ExportEventLog.csv", eventLog[0]));
                                for (var file : files.values()) {
                                    control.checkpoint();
                                    output.accept(
                                            new CdsExportPackageMemberDto(
                                                    file.relative(), file.document().xml()));
                                    String folder =
                                            file.relative()
                                                    .substring(
                                                            0,
                                                            file.relative().lastIndexOf('/') + 1);
                                    for (var attachment : file.document().attachments())
                                        output.accept(
                                                new CdsExportPackageMemberDto(
                                                        folder + attachment.relativePath(),
                                                        attachment.file()));
                                }
                            });
            owned.add(members.member());
            var archive =
                    new CdsExportPackageComposer()
                            .compose(
                                    store,
                                    members,
                                    limits.zipBytes(),
                                    limits.members(),
                                    control::checkpoint);
            owned.add(archive.archive());
            if (archive.entries() != expectedMembers) invalid();
            authorizeCohort(lease, tables, files.values(), held.keySet(), control, patients, false, audited);
            disclosure.resolveAccepted(lease.document(), Instant.now());
            control.checkpoint();
            var descriptors =
                    owned.stream()
                            .sorted(Comparator.comparing(CdsExportStagedFileDto::memberId))
                            .toList();
            var ownership =
                    store.stage(
                            "ATTEMPT_MEMBERS",
                            output -> {
                                line(
                                        output,
                                        new OwnershipHeader(
                                                "OWNERSHIP",
                                                1,
                                                lease.runId(),
                                                lease.epoch(),
                                                cohort.plaintextSha256(),
                                                descriptors.size()));
                                String previous = null;
                                for (var member : descriptors) {
                                    control.checkpoint();
                                    if (member.memberId().equals(previous)) invalid();
                                    line(output, member);
                                    previous = member.memberId();
                                }
                                line(output, new OwnershipEnd("END", descriptors.size()));
                            });
            owned.add(ownership);
            try (var input = store.open(ownership)) {
                var lines = new CdsExportJsonLines(input, 1 << 20, control::checkpoint);
                if (!same(
                        new OwnershipHeader(
                                "OWNERSHIP",
                                1,
                                lease.runId(),
                                lease.epoch(),
                                cohort.plaintextSha256(),
                                descriptors.size()),
                        lines.next())) invalid();
                for (var member : descriptors) if (!same(member, lines.next())) invalid();
                if (!same(new OwnershipEnd("END", descriptors.size()), lines.next())
                        || lines.next() != null) invalid();
            }
            authorizeCohort(lease, tables, files.values(), held.keySet(), control, patients, true, audited);
            disclosure.resolveAccepted(lease.document(), Instant.now());
            control.checkpoint();
            var result =
                    new Prepared(
                            lease.runId(),
                            lease.epoch(),
                            cohort.plaintextSha256(),
                            patients,
                            metadata,
                            archive,
                            members.member(),
                            readMe,
                            eventLog[0],
                            authorization,
                            ownership);
            complete = true;
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete) {
                for (var member : owned.reversed())
                    try {
                        store.discardUnpublished(member);
                    } catch (IOException | RuntimeException cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
                // Charts read by a job that stopped are still chart reads (census D11).
                var unaudited = new java.util.LinkedHashSet<>(read);
                unaudited.removeAll(audited);
                try {
                    auditReads(
                            lease,
                            unaudited,
                            CdsExportAuditPersister.ReadOutcome.STOPPED,
                            audited);
                } catch (RuntimeException cleanup) {
                    if (primary == null) throw cleanup;
                    // The audit store may fail again with the very same failure.
                    if (cleanup != primary) primary.addSuppressed(cleanup);
                }
            }
        }
    }

    private void auditReads(
            CdsExportLeaseDto lease,
            Collection<Integer> patients,
            CdsExportAuditPersister.ReadOutcome outcome,
            Set<Integer> audited) {
        var batch = new ArrayList<Integer>(100);
        for (int patient : patients) {
            if (audited.contains(patient)) continue;
            batch.add(patient);
            if (batch.size() == 100) {
                audit.read(lease, batch, outcome);
                audited.addAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            audit.read(lease, batch, outcome);
            audited.addAll(batch);
        }
    }

    private record Captured(
            CdsExportPatientCaptureDto source,
            CdsExportPatientDocumentService.Document document,
            CdsExportPatientFileDto name) {}

    /** One selected patient's capture, retained payload, finished document and file binding. */
    private Captured capturePatient(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            String applicationVersion,
            CdsExportXmlLimitsDto xmlLimits,
            Limits limits,
            CdsExportRunControl control,
            List<CdsExportStagedFileDto> owned,
            long[] metadataBytes)
            throws IOException {
        var source =
                captures.capture(lease, patient, tables, bounds.fields(), bounds.bytes(), control);
        own(
                owned,
                metadataBytes,
                limits.metadataBytes(),
                List.of(source.manifest(), source.capture().values(), source.capture().index()));
        var retained =
                payloads.freeze(
                        lease,
                        patient,
                        tables,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        control);
        own(
                owned,
                metadataBytes,
                limits.metadataBytes(),
                List.of(
                        retained.manifest(),
                        retained.capture().values(),
                        retained.capture().index()));
        var document =
                documents.compose(
                        lease,
                        patient,
                        tables,
                        source,
                        retained.manifest(),
                        bounds,
                        applicationVersion,
                        xmlLimits,
                        control);
        var outputs = new ArrayList<CdsExportStagedFileDto>();
        outputs.add(document.xml());
        outputs.add(document.reconciliation());
        for (var attachment : document.attachments()) outputs.add(attachment.file());
        own(owned, metadataBytes, limits.metadataBytes(), outputs);
        var name = naming.bind(lease, patient, tables, source, document, bounds, control);
        return new Captured(source, document, name);
    }

    /**
     * The safe code for a refusal that concerns only this patient's chart or this patient's
     * access, or null when the refusal concerns the whole job (limits, recipient, audit policy,
     * runtime change), which must still stop it.
     */
    static String heldCode(Exception refused) {
        if (refused instanceof CdsExportMappingReviewException mapping) return mapping.getMessage();
        if (refused instanceof SecurityException) return ACCESS_CHANGED;
        if (refused instanceof CdsExportReviewException review)
            return switch (review.reason()) {
                case UNMAPPED_DATA,
                        PREVENTION_PROGRAM_SCOPE,
                        MERGED_PATIENT_SCOPE,
                        SOURCE_CHANGED,
                        AMBIGUOUS_SOURCE,
                        INCOMPLETE_RENDER,
                        MISSING_ATTACHMENT,
                        CLINICAL_VALIDATION_FAILED,
                        CLINICAL_SOURCE_UNREVIEWED -> review.reason().code();
                case AUDIT_ACCESS_UNAVAILABLE,
                        RECIPIENT_UNAVAILABLE,
                        RESOURCE_LIMIT,
                        RUNTIME_CHANGED,
                        NO_PATIENTS_SELECTED,
                        PATIENTS_HELD,
                        CLINIC_SOURCE_UNREVIEWED -> null;
            };
        return null;
    }

    /**
     * The refusal for a job whose every selected patient was held, so there is no package. When
     * every patient was held for the same cause the job ends exactly as a one-patient job did
     * before patients could be held: the same review reason, the mapping refusal (reported as
     * unmapped data) or, when access was refused for every patient, the access refusal (decision:
     * a right missing for the whole selection refuses the job). Mixed causes report PATIENTS_HELD;
     * the per-patient reasons stay in the protected held-patient record.
     */
    static Exception allHeld(Collection<CdsExportHeldPatientDto> held) {
        var codes = new TreeSet<String>();
        for (var value : held) codes.add(value.code());
        if (codes.size() == 1) {
            String code = codes.first();
            if (ACCESS_CHANGED.equals(code)) return new SecurityException(ACCESS_CHANGED);
            for (var reason : CdsExportReviewException.Reason.values())
                if (reason.code().equals(code)) return new CdsExportReviewException(reason);
            return new CdsExportMappingReviewException(code);
        }
        return new CdsExportReviewException(CdsExportReviewException.Reason.PATIENTS_HELD);
    }

    private static Object scopeLine(Object entry) {
        if (entry instanceof CdsExportHeldPatientDto held)
            return new ScopeHeld("HELD", held.patient(), held.code());
        var file = (PatientFile) entry;
        return new ScopePatient(
                "PATIENT",
                file.document().patient(),
                file.document().sourceManifestSha256(),
                file.notes());
    }

    private record ScopeHeld(String kind, int patient, String code) {}

    private void authorizeCohort(
            CdsExportLeaseDto lease,
            Set<String> tables,
            Collection<PatientFile> files,
            Set<Integer> held,
            CdsExportRunControl control,
            long expected,
            boolean auditPrepared,
            Set<Integer> audited)
            throws IOException {
        var byPatient = new HashMap<Integer, PatientFile>();
        for (var file : files)
            if (byPatient.putIfAbsent(file.document().patient(), file) != null) invalid();
        long count = 0;
        var auditBatch = new ArrayList<Integer>(100);
        try (var cursor = cohorts.open(lease, control)) {
            CdsExportCohortService.Patient patient;
            while ((patient = cursor.next()) != null) {
                control.checkpoint();
                var file = byPatient.get(patient.patient());
                if (file == null) {
                    // A held patient is not disclosed: no source authorization or chart audit.
                    if (!held.contains(patient.patient())) invalid();
                    count++;
                    continue;
                }
                captures.authorizeSources(lease, patient, tables, control);
                for (var scope : file.notes()) {
                    control.checkpoint();
                    control.authorizeNote(CdsCategory.CLINICAL_NOTES, scope);
                }
                if (auditPrepared) {
                    auditBatch.add(patient.patient());
                    if (auditBatch.size() == 100) {
                        audit.prepared(lease, auditBatch);
                        audited.addAll(auditBatch);
                        auditBatch.clear();
                    }
                }
                count++;
            }
            if (!cursor.complete() || count != expected) invalid();
            if (!auditBatch.isEmpty()) {
                audit.prepared(lease, auditBatch);
                audited.addAll(auditBatch);
            }
        }
    }

    private record ScopeHeader(
            String kind,
            int protocol,
            String runId,
            long epoch,
            String cohortSha256,
            String compatibilityDigest,
            long patients,
            Set<String> tables) {}

    private record ScopePatient(
            String kind,
            int patient,
            String sourceManifestSha256,
            List<CdsExportNoteAccessDto> notes) {}

    private record ScopeEnd(String kind, long patients) {}

    private record OwnershipHeader(
            String kind, int protocol, String runId, long epoch, String cohortSha256, long files) {}

    private record OwnershipEnd(String kind, long files) {}

    private static boolean same(Object expected, com.fasterxml.jackson.databind.JsonNode actual)
            throws IOException {
        return actual != null && JSON.readTree(JSON.writeValueAsBytes(expected)).equals(actual);
    }

    private static void line(java.io.OutputStream output, Object value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        if (bytes.length >= (1 << 20)) limit();
        output.write(bytes);
        output.write('\n');
    }

    private static List<CdsExportPatientSummaryDto> summaries(Collection<PatientFile> files) {
        return files.stream()
                .map(
                        file -> {
                            var xml = file.document().validation();
                            boolean reports = xml.occurrences().containsKey(CdsCategory.REPORTS);
                            return new CdsExportPatientSummaryDto(
                                    file.naming(),
                                    xml.occurrences(),
                                    reports ? xml.textReports() : null,
                                    reports ? xml.binaryReports() : null,
                                    xml.xmlSha256(),
                                    xml.xmlBytes());
                        })
                .toList();
    }

    private static void own(
            List<CdsExportStagedFileDto> owned,
            long[] used,
            long maximum,
            List<CdsExportStagedFileDto> files) {
        owned.addAll(files);
        used[0] = Math.addExact(used[0], 512L * files.size());
        if (used[0] > maximum) limit();
    }

    private static final class LimitedOutput extends java.io.FilterOutputStream {
        private final long[] bytes;
        private final long maximum;

        private LimitedOutput(java.io.OutputStream output, long[] bytes, long maximum) {
            super(output);
            this.bytes = bytes;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            if (bytes[0] == maximum) limit();
            out.write(value);
            bytes[0]++;
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, value.length);
            if (length > maximum - bytes[0]) limit();
            out.write(value, offset, length);
            bytes[0] += length;
        }
    }

    /** Same-cohort private package evidence. Delivery encryption, live authority and fenced publication remain mandatory. */
    public static final class Prepared {
        private final String runId, cohortSha256;
        private final long epoch, patients;
        private final CdsExportPackageMetadataDto metadata;
        private final CdsExportPackageDto archive;
        private final CdsExportStagedFileDto members, readMe, eventLog, authorization, ownership;

        private Prepared(
                String run,
                long epoch,
                String cohort,
                long patients,
                CdsExportPackageMetadataDto metadata,
                CdsExportPackageDto archive,
                CdsExportStagedFileDto members,
                CdsExportStagedFileDto readMe,
                CdsExportStagedFileDto eventLog,
                CdsExportStagedFileDto authorization,
                CdsExportStagedFileDto ownership) {
            runId = run;
            this.epoch = epoch;
            cohortSha256 = cohort;
            this.patients = patients;
            this.metadata = metadata;
            this.archive = archive;
            this.members = members;
            this.readMe = readMe;
            this.eventLog = eventLog;
            this.authorization = authorization;
            this.ownership = ownership;
        }

        public String runId() {
            return runId;
        }

        public long epoch() {
            return epoch;
        }

        public String cohortSha256() {
            return cohortSha256;
        }

        public long patients() {
            return patients;
        }

        public CdsExportPackageMetadataDto metadata() {
            return metadata;
        }

        public CdsExportPackageDto archive() {
            return archive;
        }

        public CdsExportStagedFileDto members() {
            return members;
        }

        public CdsExportStagedFileDto readMe() {
            return readMe;
        }

        public CdsExportStagedFileDto eventLog() {
            return eventLog;
        }

        public CdsExportStagedFileDto authorization() {
            return authorization;
        }

        public CdsExportStagedFileDto ownership() {
            return ownership;
        }

        @Override
        public String toString() {
            return "CdsExportPreparedClinicalPackage[protected]";
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CLINICAL_PACKAGE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }

    private static void ambiguous() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
    }
}
