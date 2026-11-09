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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportJobService;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles;
import com.sun.security.auth.module.UnixSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/** Bounded hash-named original acceptance options; no private command/document is returned to clinical output. */
public final class CdsExportRetainedImportOptionsReader {
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(
                                            com.fasterxml.jackson.core.StreamReadFeature
                                                    .STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            com.fasterxml.jackson.core.StreamReadConstraints
                                                    .builder()
                                                    .maxNestingDepth(24)
                                                    .maxStringLength(16384)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public record Options(
            String importRunId,
            String documentSha256,
            String optionsSha256,
            String rulesetSha256,
            String sourceProfile,
            boolean synthetic,
            int timeshiftDays,
            Map<String, String> copyOptionsSha256,
            String copiesSha256) {
        public Options(
                String importRunId,
                String documentSha256,
                String optionsSha256,
                String rulesetSha256,
                String sourceProfile,
                boolean synthetic,
                int timeshiftDays) {
            this(
                    importRunId,
                    documentSha256,
                    optionsSha256,
                    rulesetSha256,
                    sourceProfile,
                    synthetic,
                    timeshiftDays,
                    synthetic ? Map.of() : Map.of("clinical", optionsSha256),
                    null);
        }

        public Options {
            if (importRunId == null
                    || !importRunId.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || documentSha256 == null
                    || !documentSha256.matches("[0-9a-f]{64}")
                    || optionsSha256 == null
                    || !optionsSha256.matches("[0-9a-f]{64}")
                    || rulesetSha256 == null
                    || !rulesetSha256.matches("[0-9a-f]{64}")
                    || sourceProfile == null
                    || sourceProfile.isEmpty()
                    || sourceProfile.length() > 32
                    || !synthetic && timeshiftDays != 0
                    || copyOptionsSha256 == null
                    || copyOptionsSha256.size() > 4096
                    || copiesSha256 != null && !copiesSha256.matches("[0-9a-f]{64}")
                    || !synthetic
                            && (!copyOptionsSha256.equals(Map.of("clinical", optionsSha256))
                                    || copiesSha256 != null)
                    || synthetic && (!copyOptionsSha256.isEmpty()) != (copiesSha256 != null))
                throw new IllegalArgumentException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_INVALID");
            var copy = new TreeMap<String, String>();
            for (var entry : copyOptionsSha256.entrySet()) {
                if (!entry.getKey()
                                .matches(
                                        synthetic
                                                ? "course-[1-9][0-9]*-student-[A-Za-z0-9_-]{1,6}-program-[1-9][0-9]*"
                                                : "clinical")
                        || entry.getValue() == null
                        || !entry.getValue().matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_RETAINED_IMPORT_OPTIONS_INVALID");
                copy.put(entry.getKey(), entry.getValue());
            }
            copyOptionsSha256 = Collections.unmodifiableMap(copy);
        }

        @Override
        public String toString() {
            return "CdsExportRetainedImportOptionsReader.Options[protected]";
        }
    }

    /** Caller supplies only bytes copied under the protected import-root policy for this captured run. */
    public Options verify(CdsExportCapturedTextRowDto run, byte[] bytes, Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (run == null
                || !"cds_import_run".equals(run.table())
                || !run.fields().keySet().equals(Set.copyOf(Plan.IMPORT_RUNS.columns()))
                || !run.recordKey().equals(run.fields().get("id"))
                || !run.exactNumbers().isEmpty()
                || !run.recordKey().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !"0".equals(run.fields().get("dry_run"))
                || bytes == null
                || bytes.length < 1
                || bytes.length > 65536
                || bytes[0] != '{')
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_SCOPE_INVALID");
        var result = parse(run, bytes);
        checkpoint.run();
        return result;
    }

    /** Reads only the captured hash-named file under the server-owned existing import root; enables no import or clinical write. */
    public Options readProtected(
            Path importRoot, CdsExportCapturedTextRowDto run, Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        byte[] bytes = null, copies = null, seal = null;
        try {
            try {
                if (run == null
                        || !"cds_import_run".equals(run.table())
                        || !run.fields().keySet().equals(Set.copyOf(Plan.IMPORT_RUNS.columns())))
                    throw new IOException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_SCOPE_INVALID");
                Path base = CdsExportProtectedFiles.dataDirectory(importRoot),
                        file =
                                CdsExportProtectedFiles.absolute(
                                        run.fields().get("snapshot_location"));
                if (!file.startsWith(base)
                        || file.equals(base)
                        || !file.getFileName().toString().matches("job-[0-9a-f]{64}\\.json"))
                    throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_LOCATION_INVALID");
                bytes = privateFile(file, 65536);

            } catch (IOException | RuntimeException unavailable) {
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_UNAVAILABLE");
            }
            var options = verify(run, bytes, checkpoint);
            if (!options.synthetic()) return options;
            Path folder = Path.of(run.fields().get("snapshot_location")).getParent();
            copies = privateFile(folder.resolve("copies.jsonl"), 4 << 20);
            seal = privateFile(folder.resolve("copies.sha256"), 65);
            return verifyCopies(run, bytes, copies, seal, checkpoint);
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            if (copies != null) Arrays.fill(copies, (byte) 0);
            if (seal != null) Arrays.fill(seal, (byte) 0);
        }
    }

    /** Verifies the immutable course-copy plan and recomputes each effective copy digest from the authenticated original command. */
    public Options verifyCopies(
            CdsExportCapturedTextRowDto run,
            byte[] documentBytes,
            byte[] copies,
            byte[] seal,
            Runnable checkpoint)
            throws IOException {
        var original = verify(run, documentBytes, checkpoint);
        if (!original.synthetic()
                || copies == null
                || copies.length < 1
                || copies.length > 4 << 20
                || seal == null
                || seal.length != 65)
            throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
        String digest = HexFormat.of().formatHex(CdsImportKeys.digest().digest(copies));
        if (!Arrays.equals(
                (digest + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII), seal))
            throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_HASH_MISMATCH");
        var command = JSON.readValue(documentBytes, CdsImportJobDocumentDto.class).command();
        var derived = new TreeMap<String, String>();
        var students = new HashSet<String>();
        var programs = new HashSet<Integer>();
        int offset = 0;
        try {
            for (int cursor = 0; cursor < copies.length; cursor++)
                if (copies[cursor] == '\n') {
                    checkpoint.run();
                    if (cursor == offset || cursor - offset > 1024 || derived.size() >= 4096)
                        throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
                    var copy =
                            JSON.readValue(
                                    copies,
                                    offset,
                                    cursor - offset,
                                    io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                            .CdsCourseCopyDto.class);
                    offset = cursor + 1;
                    if (copy == null
                            || copy.studentProviderNo() == null
                            || !copy.studentProviderNo().matches("[A-Za-z0-9_-]{1,6}")
                            || copy.programId() == null
                            || copy.programId() < 1
                            || copy.facilityId() == null
                            || copy.facilityId() < 1
                            || !Objects.equals(
                                    copy.copyKey(),
                                    "course-"
                                            + command.courseId()
                                            + "-student-"
                                            + copy.studentProviderNo()
                                            + "-program-"
                                            + copy.programId())
                            || command.facilityId() != null
                                    && !command.facilityId().equals(copy.facilityId())
                            || !students.add(copy.studentProviderNo())
                            || !programs.add(copy.programId()))
                        throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
                    var effective =
                            new io.github.carlos_emr.carlos.demographic.cdsimport.command
                                    .CdsImportCommand(
                                    command.input(),
                                    command.submissionKey(),
                                    command.providerNo(),
                                    copy.programId(),
                                    copy.facilityId(),
                                    command.sourceNamespace(),
                                    command.sourceProfile(),
                                    command.mergeExisting(),
                                    command.dryRun(),
                                    command.matchProviderNames(),
                                    command.timeshiftDays(),
                                    command.courseId(),
                                    command.synthetic());
                    if (derived.putIfAbsent(
                                    copy.copyKey(), CdsImportJobService.optionsDigest(effective))
                            != null) throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
                }
            if (offset != copies.length || derived.isEmpty())
                throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException malformed) {
            throw new IOException("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
        }
        checkpoint.run();
        return new Options(
                original.importRunId(),
                original.documentSha256(),
                original.optionsSha256(),
                original.rulesetSha256(),
                original.sourceProfile(),
                true,
                original.timeshiftDays(),
                derived,
                digest);
    }

    static void requireBinding(CdsExportCapturedTextRowDto run, Options options)
            throws IOException {
        try {
            if (run == null
                    || options == null
                    || !options.importRunId().equals(run.recordKey())
                    || !"0".equals(run.fields().get("dry_run"))
                    || !options.optionsSha256().equals(run.fields().get("options_digest"))
                    || !options.rulesetSha256().equals(run.fields().get("ruleset_digest"))
                    || !options.sourceProfile().equals(run.fields().get("profile"))
                    || !Path.of(run.fields().get("snapshot_location"))
                            .getFileName()
                            .toString()
                            .equals("job-" + options.documentSha256() + ".json"))
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
        } catch (IllegalArgumentException | NullPointerException malformed) {
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
        }
    }

    static Options selectCopies(Options original, Set<String> selected) throws IOException {
        if (selected == null
                || selected.isEmpty()
                || !original.copyOptionsSha256().keySet().containsAll(selected))
            throw new IOException("CDS_EXPORT_RETAINED_COPY_OPTIONS_MISMATCH");
        var subset = new TreeMap<String, String>();
        selected.forEach(copy -> subset.put(copy, original.copyOptionsSha256().get(copy)));
        return new Options(
                original.importRunId(),
                original.documentSha256(),
                original.optionsSha256(),
                original.rulesetSha256(),
                original.sourceProfile(),
                original.synthetic(),
                original.timeshiftDays(),
                subset,
                original.copiesSha256());
    }

    static String encodeCaptured(Options options) throws IOException {
        if (retainedWeight(options) > 2L << 20)
            throw new IOException("CDS_EXPORT_IMPORT_OPTIONS_BYTE_LIMIT");
        return JSON.writeValueAsString(options);
    }

    static Options decodeCaptured(CdsExportCapturedTextRowDto run, String encoded)
            throws IOException {
        if (encoded == null || encoded.length() < 2 || encoded.length() > 2 << 20)
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_INVALID");
        try {
            var options = JSON.readValue(encoded, Options.class);
            if (options == null)
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_INVALID");
            requireBinding(run, options);
            return options;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException malformed) {
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_INVALID");
        }
    }

    /** This row has already been authenticated with the complete native source generation. */
    public Options fromCaptured(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto index,
            String run)
            throws IOException {
        var row = index.tables().getOrDefault("retained_import_options", Map.of()).get(run);
        if (row == null
                || !row.fields().keySet().equals(Set.of("options"))
                || !row.exactNumbers().isEmpty())
            throw new IOException("CDS_EXPORT_CAPTURED_IMPORT_OPTIONS_REQUIRED");
        return decodeCaptured(
                index.tables().getOrDefault("cds_import_run", Map.of()).get(run),
                row.fields().get("options"));
    }

    static long retainedWeight(Options options) {
        long bytes = 1024L + options.sourceProfile().length() * 4L;
        for (String copy : options.copyOptionsSha256().keySet())
            bytes = Math.addExact(bytes, 256L + copy.length() * 4L);
        return bytes;
    }

    private static byte[] privateFile(Path file, int maximum) throws IOException {
        CdsExportProtectedFiles.dataDirectory(file.getParent());
        long uid =
                ((Number) Files.getAttribute(file, "unix:uid", LinkOption.NOFOLLOW_LINKS))
                        .longValue();
        int mode =
                ((Number) Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS))
                        .intValue();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || uid != new UnixSystem().getUid()
                || (mode & 07777) != 0600)
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_NOT_PRIVATE");
        return CdsProcessConfiguration.readBounded(file, maximum);
    }

    private static Options parse(CdsExportCapturedTextRowDto run, byte[] bytes) throws IOException {
        try {
            Path location = Path.of(run.fields().get("snapshot_location"));
            String hash = HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes));
            if (!location.isAbsolute()
                    || !location.equals(location.normalize())
                    || !location.getFileName().toString().equals("job-" + hash + ".json"))
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_HASH_MISMATCH");
            var document = JSON.readValue(bytes, CdsImportJobDocumentDto.class);
            if (document == null
                    || document.command() == null
                    || document.actor() == null
                    || document.jobRoot() == null)
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_INVALID");
            var command = document.command();
            var actor = document.actor();
            if (!location.getParent().equals(document.jobRoot())
                    || !Objects.equals(actor.operator(), run.fields().get("operator"))
                    || !Objects.equals(actor.providerNo(), command.providerNo())
                    || !Objects.equals(actor.facilityId(), command.facilityId())
                    || !Objects.equals(actor.programId(), command.programId())
                    || !Objects.equals(command.providerNo(), run.fields().get("provider_no"))
                    || !Objects.equals(
                            command.sourceNamespace(), run.fields().get("source_namespace"))
                    || !Objects.equals(command.sourceProfile(), run.fields().get("profile"))
                    || command.dryRun()
                    || !Objects.equals(
                            CdsImportJobService.optionsDigest(command),
                            run.fields().get("options_digest"))
                    || document.acceptedRulesetDigest() != null
                            && !Objects.equals(
                                    document.acceptedRulesetDigest(),
                                    run.fields().get("ruleset_digest"))
                    || document.snapshot() != null
                            && !Objects.equals(
                                    document.snapshot().sha256(),
                                    run.fields().get("source_digest")))
                throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
            return new Options(
                    run.recordKey(),
                    hash,
                    run.fields().get("options_digest"),
                    run.fields().get("ruleset_digest"),
                    command.sourceProfile(),
                    command.synthetic(),
                    command.timeshiftDays());
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException
                | NullPointerException invalid) {
            throw new IOException("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_INVALID");
        }
    }
}
