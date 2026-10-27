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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCourseCopyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportWorkDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsParseResultDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.validator.CdsSourceFieldCapacityValidator;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Complete bounded preflight and sealed expected work; never performs clinical mutations. */
@Service
public class CdsImportPlanningService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final CdsImportLedgerService ledger;
    private final CdsImportConfigurationService configuration;
    private final CdsSnapshotService snapshots;
    private final CdsXmlParser parser;
    private final CdsFieldParser fields;
    private final CdsPatientIdentityResolver identity;
    private final CdsImportNativeService nativeService;
    private final CdsCourseService courses;
    private final CdsImportFileService files;
    private final CdsImportJobService jobs;
    private final CdsPreflightIdentityService preflightIdentity;
    private final CdsCorrectionLookupService corrections;

    public CdsImportPlanningService(CdsImportLedgerService ledger, CdsImportConfigurationService configuration,
            CdsSnapshotService snapshots, CdsXmlParser parser, CdsFieldParser fields,
            CdsPatientIdentityResolver identity, CdsImportNativeService nativeService, CdsCourseService courses,
            CdsImportFileService files, CdsImportJobService jobs, CdsPreflightIdentityService preflightIdentity) {
        this(ledger, configuration, snapshots, parser, fields, identity, nativeService, courses, files, jobs, preflightIdentity, null);
    }

    @Autowired
    public CdsImportPlanningService(CdsImportLedgerService ledger, CdsImportConfigurationService configuration,
            CdsSnapshotService snapshots, CdsXmlParser parser, CdsFieldParser fields,
            CdsPatientIdentityResolver identity, CdsImportNativeService nativeService, CdsCourseService courses,
            CdsImportFileService files, CdsImportJobService jobs, CdsPreflightIdentityService preflightIdentity,
            CdsCorrectionLookupService corrections) {
        this.ledger = ledger; this.configuration = configuration; this.snapshots = snapshots; this.parser = parser;
        this.fields = fields; this.identity = identity; this.nativeService = nativeService; this.courses = courses;
        this.files = files; this.jobs = jobs;
        this.preflightIdentity = preflightIdentity;
        this.corrections=corrections;
    }

    public void plan(String runId, long epoch, CdsImportJobDocumentDto document, CdsSnapshotDto snapshot)
            throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        var status = ledger.status(runId);
        if (!status.rulesetDigest().equals(CdsImportJobService.rulesetDigest(document.command(), configuration))
                || !status.optionsDigest().equals(CdsImportJobService.optionsDigest(document.command()))) {
            throw new CdsImportException("IMPORT_RULESET_OR_OPTIONS_INCOMPATIBLE");
        }
        if (status.manifestDigest() != null) {
            if (status.state().equals("SEALING")) {
                ledger.resolveRevalidatedPreflightStops(runId, epoch, status.manifestDigest());
                ledger.seal(runId, epoch, status.manifestDigest(), status.expectedPatients(), status.expectedUnits());
            }
            return;
        }
        Path lockPath = document.jobRoot().resolve("planning.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                FileLock ignored = planningLock(lockChannel)) {
            planOwned(runId, epoch, document, snapshot);
        }
    }

    private void planOwned(String runId, long epoch, CdsImportJobDocumentDto document, CdsSnapshotDto snapshot)
            throws IOException {
        ledger.stage(runId, epoch, "PREFLIGHTING");
        preflightIdentity.verifySchema();
        preflightIdentity.beginScan(runId, epoch);
        Path parsedRoot = directory(document.jobRoot().resolve("parsed"), document.jobRoot());
        Path workFile = document.jobRoot().resolve("work.jsonl");
        State state = new State(runId, epoch, document, snapshot, workFile);
        try (state; var control=fields.control(state::cancelled)) {
            long reviewVersion=corrections==null ? 0 : corrections.version(runId);
            state.reviewVersion=reviewVersion;
            Path copies = freezeCopies(document);
            CdsImportKeys.add(state.manifest, "COPIES");
            CdsImportKeys.add(state.manifest, hashFile(copies));
            try {
                snapshots.forEachXml(snapshot, xml -> {
                    try { source(state, parsedRoot, copies, xml); }
                    catch (IOException failed) { throw new UncheckedIOException(failed); }
                });
            } catch (UncheckedIOException failed) { throw failed.getCause(); }
            if (state.discoveredXmls != snapshot.patientXmlFiles()) throw new CdsImportException("SOURCE_DISCOVERY_ACCOUNTING_MISMATCH");
            if (!state.parseFailure) ledger.discovery(runId, epoch, snapshot.files(), snapshot.patientXmlFiles(), snapshot.totalBytes());
            if (state.blocked) { ledger.stage(runId, epoch, "REVIEW_REQUIRED"); return; }
            if (state.patients == 0) throw new CdsImportException("NO_IDENTIFIED_PATIENTS");
            state.output.flush(); state.channel.force(true); CdsPaths.forceDirectory(document.jobRoot());
            String manifest=CdsPaths.hex(state.manifest.digest());
            if (corrections!=null) corrections.revalidated(runId, epoch, reviewVersion, manifest);
            ledger.resolveRevalidatedPreflightStops(runId, epoch, manifest);
            ledger.seal(runId, epoch, manifest, state.patients, state.units);
        }
    }

    private void source(State state, Path parsedRoot, Path copies, CdsSnapshotFileDto xml) throws IOException {
        state.discoveredXmls++;
        state.cancelled();
        Path folder = parsedRoot.resolve(CdsImportKeys.hash(xml.relativeName(), xml.sha256()));
        CdsCorrectionSetDto correction;
        CdsParseResultDto parsed;
        state.currentParseFolder=currentAttempt(folder);
        try {
            correction=corrections==null ? null : corrections.find(state.runId, xml.relativeName(), xml.sha256(), state.currentParseFolder);
            parsed = parse(xml, folder, state, correction);
        } catch (CdsInputException | CdsImportException failed) {
            if (failed instanceof CdsInputException input && operational(input.code())) {
                throw new CdsImportException(input.code().equals("CDS_INPUT_CANCELLED") ? "IMPORT_CANCELLED" : input.code());
            }
            if (failed instanceof CdsImportException imported && operational(imported.getCode())) throw imported;
            state.parseFailure = true; state.blocked = true;
            diagnostic(state, null, code(failed), "/files[" + state.discoveredXmls + "]", state.currentParseFolder.toString());
            return;
        }
        Path parseResult = parsed.recordsManifestFile().getParent().resolve("parse-result.json");
        emitSchemaWarnings(state, parsed.recordsManifestFile().getParent());
        CdsRecordDto[] demographic = new CdsRecordDto[1];
        MessageDigest patientSource = CdsPaths.sha256(); CdsImportKeys.add(patientSource, xml.sha256());
        if (correction!=null) { CdsImportKeys.add(patientSource, "REVIEWED_CORRECTIONS"); CdsImportKeys.add(patientSource, correction.sha256()); }
        boolean[] valid = {true};
        boolean[] correctionEvidence={false};
        TypedSpool typed=new TypedSpool(parsed.recordsManifestFile().getParent());
        try (typed) {
            fields.forEachRecord(parsed.recordsManifestFile(), record -> {
                try {
                    state.cancelled();
                    if (record.corrections()!=null && !correctionEvidence[0]) {
                        fields.forEachCorrectionFile(record, false, (path, digest, bytes)-> verifyArtifact(state.runId, state.epoch, path, digest, bytes));
                        fields.acceptVerifiedCorrections(record); correctionEvidence[0]=true;
                    }
                    if (record.section().equals("Demographics")) demographic[0] = record;
                    nativeService.validate(state.document.command(), record, state.snapshot, xml.relativeName());
                    if (record.section().equals("Demographics")) validateChildren(state, record, "_CONTACT", xml);
                    if (record.section().equals("CareElements")) validateChildren(state, record, "_CARE", xml);
                    if (record.section().equals("Reports")) {
                        String reference = fields.firstText(record, "FilePath", 250);
                        if (reference != null && !reference.isBlank()) {
                            CdsImportKeys.add(patientSource, snapshots.resolveReference(state.snapshot, xml.relativeName(), reference).sha256());
                        }
                    }
                } catch (IOException | CdsImportException | IllegalArgumentException failed) {
                    if (failed instanceof CdsImportException imported && operational(imported.getCode())) throw imported;
                    if (failed instanceof CdsInputException input && operational(input.code())) throw new CdsImportException(input.code());
                    valid[0] = false;
                    try {typed.append(code(failed), failurePath(failed, record), parseResult.toString()); }
                    catch (IOException unavailable) {throw new CdsImportException("PREFLIGHT_DIAGNOSTIC_WRITE_FAILED"); }
                }
            });
        } catch (RuntimeException failure) {
            if (failure instanceof UncheckedIOException io) throw io.getCause();
            throw failure;
        }
        if (Files.exists(typed.path, LinkOption.NOFOLLOW_LINKS))CdsPaths.forceFile(typed.path);
        if (demographic[0] == null) { state.blocked = true; diagnostic(state, null, "DEMOGRAPHICS_REQUIRED", "/files[" + state.discoveredXmls + "]", parseResult.toString()); return; }
        String sourceDigest = CdsPaths.hex(patientSource.digest());
        boolean[] boundTyped={false};
        forEachCopy(copies, copy -> {
            try {
                state.cancelled();
                CdsImportCommand command = copyCommand(state.document.command(), copy);
                CdsImportActorDto actor = copyActor(state.document.actor(), command);
                var source = identity.source(demographic[0], command);
                if (source.sourceId() == null || source.sourceId().isBlank()) throw new CdsImportException("SOURCE_PATIENT_ID_REQUIRED");
                String patientKey = CdsImportKeys.hash(command.sourceNamespace(), source.sourceId(), copy.copyKey());
                if (correction!=null) corrections.rebind(state.runId, state.epoch, state.reviewVersion, xml.relativeName(), xml.sha256(),
                        correction.sha256(), patientKey, sourceDigest);
                CdsImportPatientDto patient;
                try {
                    patient=ledger.planPatient(state.runId, state.epoch, patientKey, source.sourceId(), copy.copyKey(), sourceDigest);
                } catch (CdsImportException conflict) {
                    if (!conflict.getCode().equals("SOURCE_REVISION_REVIEW_REQUIRED")) throw conflict;
                    Path proof=revisionConflict(state, xml, demographic[0], parseResult, patientKey, source.sourceId(), copy.copyKey(), sourceDigest);
                    if (preflightIdentity.register(state.runId, state.epoch, patientKey, copy.copyKey(), source, false)) {
                        accountPatient(state, patientKey, sourceDigest);
                    }
                    state.blocked=true;
                    diagnostic(state, patientKey, conflict.getCode(), demographic[0].sourceXPath(), proof.toString());
                    return;
                }
                boolean declined=corrections!=null && corrections.declined(state.runId, patientKey);
                if (!preflightIdentity.register(state.runId, state.epoch, patientKey, copy.copyKey(), source, valid[0] && !declined)) {
                    ledger.diagnostic(state.runId, state.epoch, patientKey, null, "WARNING", "DUPLICATE_IDENTICAL_SOURCE_PATIENT",
                            "PREFLIGHT", demographic[0].sourceXPath(), "Repeated identical source patient was accounted for once", parseResult.toString(), false);
                    return;
                }
                accountPatient(state, patientKey, sourceDigest);
                if (declined) {
                    ledger.refusePlannedPatient(state.runId, state.epoch, patientKey, "REFUSED");
                    ledger.diagnostic(state.runId, state.epoch, patientKey, null, "WARNING", "PATIENT_EXPLICITLY_DECLINED", "PREFLIGHT",
                            demographic[0].sourceXPath(), "Operator explicitly declined this patient; no clinical units were planned", parseResult.toString(), false);
                    return;
                }
                if (!valid[0]) {
                    emitTyped(state, typed.path, patientKey); boundTyped[0]=true; state.blocked=true; return;
                }
                boolean canonicalReplay=patient.targetDemographicNo()!=null;
                if (canonicalReplay) {
                    if (!identity.matchesCanonical(source, patient.targetDemographicNo(), command)) throw new CdsImportException("CANONICAL_TARGET_IDENTITY_CONFLICT");
                } else {
                    var target = identity.resolve(source, command);
                    if (!Set.of("NO_MATCH", "UNIQUE_ELIGIBLE").contains(target.outcome().name())) {
                        ledger.refusePlannedPatient(state.runId, state.epoch, patientKey, "REFUSED");
                        diagnostic(state, patientKey, "IDENTITY_" + target.outcome().name(), demographic[0].sourceXPath(), parseResult.toString(), false);
                        return;
                    }
                }
                final String demographicUnit = unitKey(patient.lineageKey(), demographic[0], "NATIVE", 0);
                fields.forEachRecord(parsed.recordsManifestFile(), record -> {
                    try {
                        String nativeKey = unitKey(patient.lineageKey(), record, "NATIVE", 0);
                        state.unit(patientKey, nativeKey, record.section(), record.ordinal(),
                                new CdsImportWorkDto(record, state.snapshot, xml.relativeName(), xml.sha256(), parseResult, actor, command));
                        if (!record.section().equals("Demographics")) ledger.addDependency(state.runId, state.epoch, nativeKey, demographicUnit);
                        long[] page = {0};
                        fields.forEachPage(record, CdsSourceFieldService.PAGE_SIZE, slice -> {
                            try {
                                fields.forEachField(slice, field -> {
                                    try { CdsSourceFieldCapacityValidator.validate(slice, field); }
                                    catch (IOException invalid) { throw new UncheckedIOException(invalid); }
                                });
                                String key = unitKey(patient.lineageKey(), record, "FIELDS", ++page[0]);
                                state.unit(patientKey, key, record.section() + "_FIELDS", record.ordinal(),
                                        new CdsImportWorkDto(slice, state.snapshot, xml.relativeName(), xml.sha256(), parseResult, actor, command));
                                ledger.addDependency(state.runId, state.epoch, key, nativeKey);
                            } catch (IOException failed) { throw new UncheckedIOException(failed); }
                        });
                        if (record.section().equals("Demographics") || record.section().equals("CareElements")) {
                            String suffix = record.section().equals("Demographics") ? "_CONTACT" : "_CARE";
                            nativeService.forEachNativeChild(record, suffix, child -> {
                                try {
                                    String key = unitKey(patient.lineageKey(), child, suffix, child.ordinal());
                                    state.unit(patientKey, key, record.section() + suffix, child.ordinal(),
                                            new CdsImportWorkDto(child, state.snapshot, xml.relativeName(), xml.sha256(), parseResult, actor, command));
                                    ledger.addDependency(state.runId, state.epoch, key, nativeKey); ledger.addDependency(state.runId, state.epoch, key, demographicUnit);
                                } catch (IOException failed) { throw new UncheckedIOException(failed); }
                            });
                        }
                    } catch (IOException failed) { throw new UncheckedIOException(failed); }
                });
                if (canonicalReplay) ledger.requireCompleteCanonicalReplay(state.runId, state.epoch, patientKey);
            } catch (UncheckedIOException io) { throw io; }
            catch (IOException failed) { throw new UncheckedIOException(failed); }
            catch (CdsImportException | IllegalArgumentException failed) {
                if (failed instanceof CdsImportException imported && operational(imported.getCode())) throw imported;
                state.blocked = true; diagnostic(state, null, code(failed), demographic[0].sourceXPath(), parseResult.toString());
            }
        });
        if (!valid[0] && !boundTyped[0] && state.blocked) emitTyped(state, typed.path, null);
    }

    private void accountPatient(State state, String patientKey, String sourceDigest) {
        if (state.patients>=configuration.limits().maxEntries()) throw new CdsImportException("PLANNED_PATIENT_RESOURCE_LIMIT");
        state.patients++;
        CdsImportKeys.add(state.manifest, "PATIENT"); CdsImportKeys.add(state.manifest, patientKey); CdsImportKeys.add(state.manifest, sourceDigest);
    }

    /** Immutable private evidence for an explicit review; canonical revision never happens during discovery. */
    private Path revisionConflict(State state, CdsSnapshotFileDto xml, CdsRecordDto demographic, Path parseResult,
            String patientKey, String sourcePatientId, String copyKey, String sourceDigest) throws IOException {
        var revision=ledger.unwrittenRevision(state.runId, state.epoch, sourcePatientId, copyKey, sourceDigest);
        if (!state.runId.equals(revision.runId()) || !patientKey.equals(revision.patientKey())
                || !sourceDigest.equals(revision.newSourceDigest())) throw new CdsImportException("REVISION_PROOF_IDENTITY_MISMATCH");
        var identified=ledger.recordRevisionConflictPatient(state.runId, state.epoch, sourcePatientId, copyKey, sourceDigest);
        if (!patientKey.equals(identified.patientKey())) throw new CdsImportException("REVISION_PROOF_IDENTITY_MISMATCH");
        var root=JSON.createObjectNode().put("version", 1).put("sourceMember", xml.relativeName())
                .put("sourceXmlSha256", xml.sha256()).put("recordXPath", demographic.sourceXPath())
                .put("parseResultFile", parseResult.toString()).put("patientKey", patientKey)
                .put("newPatientSourceDigest", sourceDigest);
        root.set("revision", JSON.valueToTree(revision));
        byte[] bytes=JSON.writeValueAsBytes(root);
        if (bytes.length>65536) throw new CdsImportException("REVISION_PROOF_BYTE_LIMIT");
        String hash=CdsPaths.hex(CdsPaths.sha256().digest(bytes));
        Path proof=CdsPaths.within(parseResult.getParent().resolve("revision-conflict-"+hash+".json"), state.document.jobRoot());
        if (Files.exists(proof, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(proof) || Files.size(proof)!=bytes.length || !hashFile(proof).equals(hash)) {
                throw new CdsImportException("REVISION_PROOF_HASH_MISMATCH");
            }
        } else {
            try (OutputStream output=CdsPaths.newPrivateFile(proof, proof.getParent())) { output.write(bytes); }
            CdsPaths.forceFile(proof); CdsPaths.forceDirectory(proof.getParent());
        }
        return proof;
    }

    private void validateChildren(State state, CdsRecordDto record, String suffix, CdsSnapshotFileDto xml) throws IOException {
        try {
            nativeService.forEachNativeChild(record, suffix, child -> {
                try {state.cancelled(); nativeService.validate(state.document.command(), child, state.snapshot, xml.relativeName()); }
                catch (IOException | CdsImportException | IllegalArgumentException failed) {
                    if (failed instanceof CdsImportException imported && operational(imported.getCode())) throw imported;
                    try {throw new UncheckedIOException(new CdsInputException(code(failed), failurePath(failed, child), "")); }
                    catch (IOException lookupFailure) {throw new UncheckedIOException(lookupFailure); }
                }
            });
        } catch (UncheckedIOException failed) { throw failed.getCause(); }
    }

    private void emitTyped(State state, Path spool, String patient) throws IOException {
        try (InputStream input=new BufferedInputStream(Files.newInputStream(spool, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] line;
            while ((line=CdsFieldParser.readMetadataLine(input))!=null) {
                state.cancelled(); JsonNode row=JSON.readTree(line);
                diagnostic(state, patient, row.path("code").asText(), row.path("sourceXPath").asText(), row.path("detail").asText());
            }
        }
    }

    private final class TypedSpool implements AutoCloseable {
        final Path path; OutputStream output; long bytes, nextDisk;
        TypedSpool(Path folder) throws IOException {
            path=folder.resolve("typed-errors-"+UUID.randomUUID()+".jsonl");
        }
        void append(String code, String xpath, String detail) throws IOException {
            if (output==null)output=new BufferedOutputStream(CdsPaths.newPrivateFile(path, path.getParent()), 65536);
            byte[] row=JSON.writeValueAsBytes(JSON.createObjectNode().put("code", code).put("sourceXPath", xpath).put("detail", detail));
            bytes=Math.addExact(bytes, row.length+1L); var limits=configuration.limits();
            if (bytes>limits.maxExpandedBytes()) throw new CdsImportException("PREFLIGHT_DIAGNOSTIC_BYTE_LIMIT");
            if (bytes>=nextDisk) {
                if (Files.getFileStore(path.getParent()).getUsableSpace()<limits.minimumFreeDiskBytes()) throw new CdsImportException("CDS_INPUT_DISK_RESERVE");
                nextDisk=bytes+(16L<<20);
            }
            output.write(row); output.write('\n');
        }
        @Override public void close() throws IOException {if (output!=null)output.close(); }
    }

    private void emitSchemaWarnings(State state, Path folder) throws IOException {
        JsonNode proof=JSON.readTree(CdsImportJobService.boundedFile(folder.resolve("parse.seal"), 65536));
        if (proof.path("schemaWarnings").asLong()==0)return;
        Path details=folder.resolve("validation-errors.jsonl");
        try (InputStream input=new BufferedInputStream(Files.newInputStream(details, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] line;
            while ((line=CdsFieldParser.readMetadataLine(input))!=null) {
                state.cancelled(); JsonNode row=JSON.readTree(line);
                if (row.path("severity").asText().equals("WARNING"))ledger.diagnostic(state.runId, state.epoch, null, null, "WARNING",
                        row.path("code").asText("CDS_XML_SCHEMA_WARNING"), "SCHEMA_VALIDATION", row.path("sourceXPath").asText(),
                        "Source XML validation emitted a nonblocking warning", details.toString(), false);
            }
        }
    }

    private CdsParseResultDto parse(CdsSnapshotFileDto xml, Path baseFolder, State state, CdsCorrectionSetDto correction) throws IOException {
        Path folder=currentAttempt(baseFolder);
        Path result=folder.resolve("parse-result.json");
        boolean rebuild=false;
        if (Files.exists(result, LinkOption.NOFOLLOW_LINKS) && Files.exists(folder.resolve("parse.seal"), LinkOption.NOFOLLOW_LINKS)) {
            try {
                JsonNode seal=JSON.readTree(CdsImportJobService.boundedFile(folder.resolve("parse.seal"), 65536));
                if (seal.path("correctionsSha256").asText().equals(correction==null ? "" : correction.sha256())) {
                    CdsParseResultDto cached=JSON.readValue(CdsImportJobService.boundedFile(result, 65536), CdsParseResultDto.class);
                    if (!cached.sourceSha256().equals(xml.sha256()) || !cached.valuesFile().getParent().equals(folder)
                            || !cached.recordsManifestFile().equals(folder.resolve("records.jsonl"))
                            || !cached.valuesFile().equals(folder.resolve("values.bin"))
                            || cached.sourceBytes()!=xml.length() || Files.size(cached.valuesFile())!=cached.valuesBytes()) {
                        throw new CdsImportException("PARSED_SOURCE_CHANGED");
                    }
                    verifyParseMetadata(folder, cached, true);
                    return cached;
                }
            } catch (CdsImportException invalid) {
                if (!Set.of("PARSED_SOURCE_CHANGED", "PARSED_METADATA_HASH_MISMATCH").contains(invalid.getCode())) throw invalid;
                rebuild=true;
            } catch (CdsInputException invalid) {
                if (operational(invalid.code())) throw invalid;
                rebuild=true;
            } catch (IOException | IllegalArgumentException invalid) {
                rebuild=true;
            }
        } else if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            rebuild=true;
        }
        if (rebuild) {
            state.cancelled();
            ledger.diagnostic(state.runId, state.epoch, null, null, "WARNING", "CDS_PARSE_CACHE_REBUILT_FROM_ACCEPTED_SOURCE",
                    "PREFLIGHT", "/files["+state.discoveredXmls+"]",
                    "An incomplete or invalid unsealed parse cache was retained; the accepted source is being fully revalidated", folder.toString(), false);
        }
        if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) folder=baseFolder.resolveSibling(baseFolder.getFileName()+"-attempt-"+UUID.randomUUID());
        state.currentParseFolder=folder;
        selectAttempt(baseFolder, folder);
        result=folder.resolve("parse-result.json");
        CdsParseResultDto parsed=parser.parse(xml.path(), folder, configuration.limits(), state::cancellationRequested, record-> { }, correction);
        if (!parsed.sourceSha256().equals(xml.sha256())) throw new CdsImportException("SNAPSHOT_XML_CHANGED");
        try (OutputStream output=CdsPaths.newPrivateFile(result, folder)) { output.write(JSON.writeValueAsBytes(parsed)); }
        CdsPaths.forceFile(result); CdsPaths.forceDirectory(folder);
        return parsed;
    }

    private static Path currentAttempt(Path baseFolder) throws IOException {
        Path pointer=baseFolder.resolveSibling(baseFolder.getFileName()+".current.json");
        if (!Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) return baseFolder;
        JsonNode value=JSON.readTree(CdsImportJobService.boundedFile(pointer, 65536));
        String name=value.path("directory").asText();
        if (!name.matches(java.util.regex.Pattern.quote(baseFolder.getFileName().toString())+"(?:-attempt-[0-9a-f-]{36})?")) {
            throw new CdsImportException("PARSE_ATTEMPT_POINTER_INVALID");
        }
        return CdsPaths.within(baseFolder.getParent().resolve(name), baseFolder.getParent());
    }

    private static void selectAttempt(Path baseFolder, Path folder) throws IOException {
        Path root=baseFolder.getParent(), pending=root.resolve("attempt-pointer-"+UUID.randomUUID()+".pending");
        try (OutputStream output=CdsPaths.newPrivateFile(pending, root)) {
            output.write(JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("directory", folder.getFileName().toString())));
        }
        CdsPaths.forceFile(pending);
        Files.move(pending, baseFolder.resolveSibling(baseFolder.getFileName()+".current.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        CdsPaths.forceDirectory(root);
    }

    public CdsImportWorkDto work(String runId, CdsImportUnitDto unit) throws IOException {
        String location = unit.spoolLocation(); int separator = location.lastIndexOf('#');
        if (separator < 1) throw new CdsImportException("WORK_LOCATION_INVALID");
        Path root = jobs.root(runId), path = CdsPaths.within(Path.of(location.substring(0, separator)), root);
        String[] parts = location.substring(separator + 1).split(":", -1);
        if (parts.length != 3 || !parts[2].matches("[0-9a-f]{64}")) throw new CdsImportException("WORK_LOCATION_INVALID");
        long offset, length;
        try { offset = Long.parseLong(parts[0]); length = Long.parseLong(parts[1]); }
        catch (NumberFormatException invalid) { throw new CdsImportException("WORK_LOCATION_INVALID"); }
        if (offset < 0 || length < 1 || length > 65536) throw new CdsImportException("WORK_LOCATION_INVALID");
        byte[] bytes = new byte[(int) length];
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (offset > file.size() || length > file.size() - offset) throw new CdsImportException("WORK_SPOOL_TRUNCATED");
            file.position(offset); ByteBuffer target = ByteBuffer.wrap(bytes);
            while (target.hasRemaining()) if (file.read(target) < 0) throw new CdsImportException("WORK_SPOOL_TRUNCATED");
        }
        if (!parts[2].equals(CdsPaths.hex(CdsPaths.sha256().digest(bytes)))) throw new CdsImportException("WORK_SPOOL_HASH_MISMATCH");
        CdsImportWorkDto value = JSON.readValue(bytes, CdsImportWorkDto.class);
        if (!value.record().effectiveSha256().equals(unit.sourceDigest())) throw new CdsImportException("WORK_SOURCE_HASH_MISMATCH");
        if (value.record().corrections()!=null && !value.record().corrections().acceptedXmlSha256().equals(value.xmlSha256())) {
            throw new CdsImportException("WORK_CORRECTION_SOURCE_MISMATCH");
        }
        CdsPaths.within(value.record().fieldsFile(), root); CdsPaths.within(value.record().valuesFile(), root);
        if (value.record().corrections()!=null) CdsPaths.within(value.record().corrections().manifestFile(), root);
        CdsPaths.within(value.snapshot().root(), root); CdsPaths.within(value.parseResultFile(), root);
        return value;
    }

    /** Streams protected, precise schema evidence into an already authorized operator report. */
    public void forEachValidationDiagnostic(String runId, Consumer<Map<String, Object>> consumer) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        CdsImportJobDocumentDto document=jobs.load(runId);
        Path published=document.jobRoot().resolve("snapshot");
        if (!Files.isDirectory(published, LinkOption.NOFOLLOW_LINKS)) return;
        CdsSnapshotDto snapshot=snapshots.load(published, false);
        try {
            snapshots.forEachXml(snapshot, xml -> {
                Path folder;
                try { folder=currentAttempt(CdsPaths.within(document.jobRoot().resolve("parsed").resolve(CdsImportKeys.hash(xml.relativeName(), xml.sha256())), document.jobRoot())); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
                Path diagnostics=folder.resolve("validation-errors.jsonl");
                if (!Files.exists(diagnostics, LinkOption.NOFOLLOW_LINKS)) return;
                try (InputStream input=new BufferedInputStream(Files.newInputStream(diagnostics, LinkOption.NOFOLLOW_LINKS), 65536)) {
                    byte[] line;
                    while ((line=CdsFieldParser.readMetadataLine(input))!=null) {
                        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("IMPORT_CANCELLED");
                        JsonNode value=JSON.readTree(line); Map<String, Object> row=new LinkedHashMap<>();
                        row.put("diagnosticKind", "source-schema"); row.put("severity", value.path("severity").asText("ERROR")); row.put("blocking", value.path("blocking").asBoolean(true));
                        row.put("code", value.path("code").asText("CDS_XML_SCHEMA_INVALID")); row.put("stage", "SCHEMA_VALIDATION");
                        row.put("sourceMember", xml.relativeName()); row.put("sourceXmlSha256", xml.sha256());
                        row.put("sourceXPath", value.path("sourceXPath").asText()); row.put("line", value.path("line").asLong(-1));
                        row.put("column", value.path("column").asLong(-1)); row.put("protectedDetailPreview", value.path("protectedDetailPreview").asText());
                        row.put("detailPreviewLimited", value.path("detailPreviewLimited").asBoolean()); consumer.accept(Map.copyOf(row));
                    }
                } catch (IOException failed) { throw new UncheckedIOException(failed); }
            });
        } catch (UncheckedIOException failed) { throw failed.getCause(); }
    }

    /** Whole value-file digest was computed once while parsing; publication never hashes per field page. */
    public void publishValues(String runId, long epoch, CdsImportWorkDto work) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        Path metadata = CdsPaths.within(work.parseResultFile().getParent().resolve("values-meta.json"), jobs.root(runId));
        JsonNode value = JSON.readTree(CdsImportJobService.boundedFile(metadata, 65536));
        JsonNode sealed=JSON.readTree(CdsImportJobService.boundedFile(metadata.getParent().resolve("parse.seal"), 65536));
        if (!hashFile(metadata).equals(sealed.path("metadataSha256").asText())
                || !value.path("sha256").asText().equals(sealed.path("valuesSha256").asText())) throw new CdsImportException("VALUE_METADATA_HASH_MISMATCH");
        Path location = metadata.getParent().resolve(value.path("valuesFile").asText());
        if (!location.equals(work.record().valuesFile()) || Files.size(location) != value.path("length").asLong(-1)) {
            throw new CdsImportException("VALUE_SPOOL_PUBLICATION_CONFLICT");
        }
        String expected=value.path("sha256").asText(); long length=value.path("length").asLong();
        publishArtifact(runId, epoch, location, expected, length);
        var corrections=work.record().corrections();
        if (corrections!=null) {
            Path manifest=corrections.manifestFile(); long manifestBytes=Files.size(manifest);
            if (manifestBytes>(1<<20)) throw new CdsImportException("CDS_CORRECTION_MANIFEST_CAPACITY_EXCEEDED");
            if (!files.verifiedFor(runId, epoch, manifest.toString(), corrections.sha256(), manifestBytes)) {
                // Publish the manifest's epoch proof LAST: it is the bounded group-completion
                // marker for all of this XML's reviewed values, including other record sections.
                verifyArtifact(runId, epoch, manifest, corrections.sha256(), manifestBytes);
                fields.forEachCorrectionFile(work.record(), false, (path, digest, bytes)-> {
                    if (!path.equals(manifest)) publishArtifact(runId, epoch, path, digest, bytes);
                });
                files.publishedVerified(runId, epoch, manifest.toString(), corrections.sha256(), manifestBytes);
            }
            fields.acceptVerifiedCorrections(work.record());
        }
    }

    private void publishArtifact(String runId, long epoch, Path location, String expected, long length) throws IOException {
        if (files.verifiedFor(runId, epoch, location.toString(), expected, length)) return;
        verifyArtifact(runId, epoch, location, expected, length);
        files.publishedVerified(runId, epoch, location.toString(), expected, length);
    }

    private void verifyArtifact(String runId, long epoch, Path location, String expected, long length) throws IOException {
        if (Files.size(location)!=length) throw new CdsImportException("VALUE_SPOOL_HASH_MISMATCH");
        MessageDigest digest=CdsPaths.sha256(); long bytes=0, nextCancellation=0;
        long started=System.nanoTime(), deadline=configuration.limits().maxDurationMillis();
        try (InputStream input=Files.newInputStream(location, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536]; int n;
            while ((n=input.read(buffer))>=0) {
                if (Thread.currentThread().isInterrupted()) throw new CdsImportException("IMPORT_CANCELLED");
                long now=System.nanoTime();
                if ((now-started)/1_000_000>deadline) throw new CdsImportException("CDS_INPUT_DEADLINE");
                if (now>=nextCancellation) {
                    var status=ledger.status(runId);
                    if (status.ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
                    if (status.cancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
                    nextCancellation=now+250_000_000L;
                }
                bytes=Math.addExact(bytes, n);
                if (bytes>length) throw new CdsImportException("VALUE_SPOOL_HASH_MISMATCH");
                digest.update(buffer, 0, n);
            }
        }
        if (bytes!=length || !expected.equals(CdsPaths.hex(digest.digest()))) throw new CdsImportException("VALUE_SPOOL_HASH_MISMATCH");
    }

    private void verifyParseMetadata(Path folder, CdsParseResultDto parsed, boolean verifyFiles) throws IOException {
        JsonNode seal=JSON.readTree(CdsImportJobService.boundedFile(folder.resolve("parse.seal"), 65536));
        if (seal.path("version").asInt()!=2 || !parsed.sourceSha256().equals(seal.path("sourceSha256").asText())
                || parsed.sourceBytes()!=seal.path("sourceBytes").asLong(-1) || parsed.records()!=seal.path("records").asLong(-1)
                || parsed.fields()!=seal.path("fields").asLong(-1) || parsed.valuesBytes()!=seal.path("valuesBytes").asLong(-1)
                || !parsed.valuesSha256().equals(seal.path("valuesSha256").asText())
                || !hashFile(folder.resolve("values-meta.json")).equals(seal.path("metadataSha256").asText())
                || verifyFiles && (!hashFile(folder.resolve("fields.jsonl")).equals(seal.path("fieldsSha256").asText())
                        || !hashFile(folder.resolve("records.jsonl")).equals(seal.path("recordsSha256").asText()))) {
            throw new CdsImportException("PARSED_METADATA_HASH_MISMATCH");
        }
    }

    private Path freezeCopies(CdsImportJobDocumentDto document) throws IOException {
        Path path = document.jobRoot().resolve("copies.jsonl");
        Path seal = document.jobRoot().resolve("copies.sha256");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.exists(seal, LinkOption.NOFOLLOW_LINKS)) publishCopySeal(seal, document.jobRoot(), hashFile(path));
            String expected = new String(CdsImportJobService.boundedFile(seal, 65), StandardCharsets.US_ASCII).trim();
            if (!expected.matches("[0-9a-f]{64}") || !hashFile(path).equals(expected)) throw new CdsImportException("FROZEN_COPIES_HASH_MISMATCH");
            return path;
        }
        Path attempt = document.jobRoot().resolve("copies-" + UUID.randomUUID() + ".pending");
        try (OutputStream output = new BufferedOutputStream(CdsPaths.newPrivateFile(attempt, document.jobRoot()), 65536)) {
            long[] count={0}, bytes={0}; var limits=configuration.limits();
            Consumer<CdsCourseCopyDto> save = copy -> {
                try {
                    fields.checkpoint();
                    byte[] row=JSON.writeValueAsBytes(copy);
                    count[0]++; bytes[0]=Math.addExact(bytes[0], row.length+1L);
                    if (count[0]>limits.maxEntries() || bytes[0]>limits.maxExpandedBytes()) throw new CdsImportException("FROZEN_COPIES_RESOURCE_LIMIT");
                    output.write(row); output.write('\n');
                }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            };
            if (document.command().synthetic()) courses.forEachCopy(document.actor(), document.command(), save);
            else save.accept(new CdsCourseCopyDto("clinical", document.command().providerNo(), document.command().programId(), document.command().facilityId()));
        } catch (UncheckedIOException failed) { throw failed.getCause(); }
        CdsPaths.forceFile(attempt);
        String digest = hashFile(attempt);
        Files.move(attempt, path, StandardCopyOption.ATOMIC_MOVE); CdsPaths.forceDirectory(document.jobRoot());
        publishCopySeal(seal, document.jobRoot(), digest);
        return path;
    }

    private static void publishCopySeal(Path seal, Path root, String digest) throws IOException {
        Path pending=root.resolve("copy-seal-" + UUID.randomUUID() + ".pending");
        try (OutputStream output = CdsPaths.newPrivateFile(pending, root)) {
            output.write((digest + "\n").getBytes(StandardCharsets.US_ASCII));
        }
        CdsPaths.forceFile(pending); Files.move(pending, seal, StandardCopyOption.ATOMIC_MOVE); CdsPaths.forceDirectory(root);
    }

    private static void forEachCopy(Path path, Consumer<CdsCourseCopyDto> consumer) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] row; while ((row = CdsFieldParser.readMetadataLine(input)) != null) consumer.accept(JSON.readValue(row, CdsCourseCopyDto.class));
        }
    }

    private static CdsImportCommand copyCommand(CdsImportCommand value, CdsCourseCopyDto copy) {
        return new CdsImportCommand(value.input(), value.submissionKey(), value.providerNo(), copy.programId(), copy.facilityId(),
                value.sourceNamespace(), value.sourceProfile(), value.mergeExisting(), value.dryRun(), value.matchProviderNames(),
                value.timeshiftDays(), value.courseId(), value.synthetic());
    }
    private static CdsImportActorDto copyActor(CdsImportActorDto actor, CdsImportCommand command) {
        return new CdsImportActorDto(actor.operator(), actor.providerNo(), actor.operatorProviderNo(), actor.securityNo(), command.facilityId(), command.programId());
    }
    private static String unitKey(String lineage, CdsRecordDto record, String role, long page) {
        return CdsImportKeys.hash(lineage, record.sourceXPath(), role, Long.toString(page));
    }
    private static Path directory(Path path, Path root) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) CdsPaths.privateDirectory(path, root);
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new CdsImportException("PLANNING_STORAGE_INVALID");
        return path;
    }
    private void diagnostic(State state, String patient, String code, String xpath, String detail) {
        diagnostic(state, patient, code, xpath, detail, true);
    }
    private void diagnostic(State state, String patient, String code, String xpath, String detail, boolean blocking) {
        ledger.diagnostic(state.runId, state.epoch, patient, null, blocking ? "ERROR" : "WARNING", code,
                "PREFLIGHT", xpath, blocking ? "Source requires correction or explicit review before import" : "Patient refused by the selected identity policy",
                detail, blocking);
    }
    private static String code(Exception failure) {
        if (failure instanceof CdsInputException input) return input.code();
        if (failure instanceof CdsClinicalException clinical) return clinical.code();
        if (failure instanceof CdsImportException imported) return imported.getCode();
        String code = failure.getMessage();
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,95}") ? code : "SOURCE_PREFLIGHT_FAILED";
    }

    private static boolean operational(String code) {
        return code.endsWith("_RESOURCE_LIMIT") || code.endsWith("_BYTE_LIMIT") || code.endsWith("_DEPTH_LIMIT")
                || code.endsWith("_PATH_LIMIT") || code.endsWith("_CHILD_NAME_LIMIT")
                || Set.of("CDS_INPUT_CANCELLED", "CDS_INPUT_DEADLINE", "CDS_INPUT_DISK_RESERVE", "CDS_DIAGNOSTIC_WRITE_FAILED",
                        "CDS_SECURE_XML_PARSER_UNAVAILABLE", "CDS_SPOOL_CLOSE_FAILED", "CDS_SPOOL_IO_FAILED", "CDS_XML_READ_FAILED",
                        "OWNER_FENCED", "IMPORT_CANCELLED").contains(code);
    }

    private static String failurePath(Exception failure, String fallback) {
        if (failure instanceof CdsInputException input && !input.sourceXPath().isBlank()) return input.sourceXPath();
        if (failure instanceof CdsClinicalException clinical && !clinical.field().isBlank()) {
            return fallback + (clinical.field().startsWith("/") ? "" : "/") + clinical.field();
        }
        return fallback;
    }

    private String failurePath(Exception failure, CdsRecordDto record) throws IOException {
        String requested=failurePath(failure, record.sourceXPath());
        if (requested.equals(record.sourceXPath())) return requested;
        String normalized=CdsFieldParser.unindexed(requested);
        String[] exact={null}, descendant={null}, literal={null}; int[] exactCount={0}, descendants={0};
        fields.forEachField(record, field-> {
            String full=record.sourceXPath()+(field.path().isEmpty() ? "" : "/"+field.path());
            if (full.equals(requested)) literal[0]=full;
            String candidate=CdsFieldParser.unindexed(full);
            if (candidate.equals(normalized)) {exact[0]=full; exactCount[0]++; }
            else if (candidate.startsWith(normalized+"/")) {descendant[0]=full; descendants[0]++; }
        });
        if (literal[0]!=null) return literal[0];
        if (exactCount[0]==1) return exact[0];
        return exactCount[0]==0 && descendants[0]==1 ? descendant[0] : requested;
    }

    private static FileLock planningLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new CdsImportException("IMPORT_PLANNING_STORAGE_BUSY");
            return lock;
        } catch (OverlappingFileLockException busy) { throw new CdsImportException("IMPORT_PLANNING_STORAGE_BUSY"); }
    }

    private String hashFile(Path path) throws IOException {
        MessageDigest digest = CdsPaths.sha256();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) >= 0) { fields.checkpoint(); digest.update(buffer, 0, n); }
        }
        return CdsPaths.hex(digest.digest());
    }

    private final class State implements AutoCloseable {
        final String runId; final long epoch; final CdsImportJobDocumentDto document; final CdsSnapshotDto snapshot;
        final Path workFile; final FileChannel channel; final OutputStream output;
        final MessageDigest manifest = CdsPaths.sha256();
        long position; long patients; long units; long discoveredXmls; boolean blocked; boolean parseFailure;
        long reviewVersion;
        long nextDiskCheck;
        Path currentParseFolder;
        final long started=System.nanoTime();
        long nextCancellationPoll;
        State(String runId, long epoch, CdsImportJobDocumentDto document, CdsSnapshotDto snapshot, Path workFile) throws IOException {
            this.runId = runId; this.epoch = epoch; this.document = document; this.snapshot = snapshot; this.workFile = workFile;
            channel = FileChannel.open(CdsPaths.within(workFile, document.jobRoot()),
                    Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            position = channel.size(); channel.position(position); output = new BufferedOutputStream(java.nio.channels.Channels.newOutputStream(channel), 65536);
        }
        void cancelled() {
            if (Thread.currentThread().isInterrupted() || cancellationRequested()) throw new CdsImportException("IMPORT_CANCELLED");
        }
        boolean cancellationRequested() {
            long now=System.nanoTime();
            if ((now-started)/1_000_000>configuration.limits().maxDurationMillis()) throw new CdsImportException("CDS_INPUT_DEADLINE");
            if (now<nextCancellationPoll) return false; nextCancellationPoll=now+250_000_000L;
            var status=ledger.status(runId);
            if (status.ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
            return status.cancelRequested();
        }
        void unit(String patient, String key, String category, long ordinal, CdsImportWorkDto work) throws IOException {
            cancelled(); byte[] bytes = JSON.writeValueAsBytes(work);
            if (bytes.length > 65536) throw new CdsImportException("WORK_METADATA_CAPACITY_EXCEEDED");
            long offset = position; output.write(bytes); output.write('\n'); output.flush(); channel.force(false);
            position = Math.addExact(position, bytes.length + 1L);
            var limits=configuration.limits();
            if (position > limits.maxExpandedBytes()) throw new CdsImportException("PLANNING_SPOOL_BYTE_LIMIT");
            if (position >= nextDiskCheck) {
                if (Files.getFileStore(document.jobRoot()).getUsableSpace() < limits.minimumFreeDiskBytes()) throw new CdsImportException("PLANNING_DISK_RESERVE");
                nextDiskCheck=position+(16L<<20);
            }
            String reference = workFile + "#" + offset + ":" + bytes.length + ":" + CdsPaths.hex(CdsPaths.sha256().digest(bytes));
            ledger.planUnit(runId, epoch, patient, key, category, ordinal, work.record().effectiveSha256(), reference); units++;
            CdsImportKeys.add(manifest, "UNIT"); CdsImportKeys.add(manifest, key); CdsImportKeys.add(manifest, work.record().effectiveSha256());
        }
        @Override public void close() throws IOException { output.close(); }
    }
}
