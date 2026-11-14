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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCohortManifestDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMergeGroupDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fully verifies selection bytes before issuing a durable cohort proof. Native capture can receive
 * a patient scope only from a current cursor over that sealed cohort, including physician rosters.
 * Preparing files and publishing their durable pointer are separate boundaries: after prepare(),
 * the caller passes Sealed to ledger.sealSelection(). A lost DB acknowledgement retains private
 * files and retries that exact proof; it never deletes a possibly committed selection or silently
 * reruns discovery. No clinical completeness, output validation or READY is implied by a seal.
 *
 * <p>No selection is refused for its size. The manifest binds the exact job document, whose
 * explicit patient list can be as long as the request; a long one is stored in two lines, as the
 * ledger stores the job document itself ({@link #write}).
 *
 * <p>A selection grouped by Merge Records (manifest protocol 2) binds its frozen group plan as
 * well. The cursor then yields one scope per patient file in ascending order of its chart: each
 * selected chart in no group, and each group opened on its head, whose other charts are reached
 * through {@link Patient#member}. The plan is verified against the selection before any scope is
 * issued: heads ascending, every chart in one group only, and every selected chart of a group
 * present in the selection.
 */
@Service
public class CdsExportCohortService {
    /**
     * A manifest of at most this many bytes is stored as its JSON, byte for byte as before; a
     * longer one, which only a long explicit selection makes, is stored in two lines ({@link
     * #write}). It decides how a manifest is stored, never whether. A first line longer than this
     * was not written here: everything left on it has a fixed greatest length.
     */
    private static final int ONE_LINE = 524_288;
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(24)
                                                    .maxStringLength(262_144)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
    private final CdsExportStagingFactory stores;
    private final CdsExportLedgerService ledger;

    public CdsExportCohortService(CdsExportStagingFactory stores, CdsExportLedgerService ledger) {
        this.stores = stores;
        this.ledger = ledger;
    }

    /** The caller already owns this selection file and must retain it across an uncertain subsequent DB seal. */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public Sealed prepare(
            CdsExportLeaseDto lease, CdsExportSelectionDto selection, CdsExportRunControl control)
            throws IOException {
        outside();
        Objects.requireNonNull(control);
        Objects.requireNonNull(selection);
        control.checkpoint();
        requireBound(lease);
        if (selection.patients() < 1) throw new IOException("CDS_EXPORT_NO_PATIENTS_SELECTED");
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        verifySelection(store, lease.document().command(), selection, control);
        // A selection grouped by Merge Records binds its group plan too (protocol 2).
        var manifest =
                new CdsExportCohortManifestDto(
                        selection.groups() == null ? 1 : 2,
                        lease.runId(),
                        lease.epoch(),
                        lease.document(),
                        selection);
        CdsExportStagedFileDto member = null;
        boolean complete = false;
        Throwable primary = null;
        try {
            member = store.stage("COHORT_MANIFEST", output -> write(manifest, output));
            var readback = manifest(store, member);
            if (!readback.equals(manifest)) invalid();
            control.checkpoint();
            var result = new Sealed(lease, manifest, member);
            complete = true;
            return result;
        } catch (IOException | RuntimeException | Error failed) {
            primary = failed;
            throw failed;
        } finally {
            if (!complete && member != null)
                try {
                    store.discardUnpublished(member);
                } catch (IOException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    /** Opens a prepared seal only after the ledger has durably accepted that exact pointer under the live owner. */
    public Cursor open(Sealed sealed, CdsExportRunControl control) throws IOException {
        outside();
        Objects.requireNonNull(sealed);
        Objects.requireNonNull(control);
        control.checkpoint();
        if (!ledger.cohort(sealed.lease).equals(sealed.member)) invalid();
        var store =
                stores.openExisting(
                        sealed.lease.runId(),
                        sealed.lease.epoch(),
                        sealed.lease.document().storage(),
                        control::checkpoint);
        var plan = plan(store, sealed.manifest.selection(), control::checkpoint);
        return new Cursor(
                sealed, store.open(sealed.manifest.selection().inventory()), plan, control);
    }

    /** Recovery within a live attempt loads its exact committed manifest; it never rediscovers a roster. */
    public Cursor open(CdsExportLeaseDto lease, CdsExportRunControl control) throws IOException {
        outside();
        requireBound(lease);
        control.checkpoint();
        var member = ledger.cohort(lease);
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var manifest = manifest(store, member);
        if (!manifest.runId().equals(lease.runId())
                || manifest.epoch() != lease.epoch()
                || !manifest.intent().equals(lease.document())) invalid();
        verifySelection(store, lease.document().command(), manifest.selection(), control);
        return new Cursor(
                new Sealed(lease, manifest, member),
                store.open(manifest.selection().inventory()),
                plan(store, manifest.selection(), control::checkpoint),
                control);
    }

    @FunctionalInterface
    public interface RetainedPatients {
        void accept(CdsExportSelectionRowDto patient) throws IOException;
    }

    /**
     * One patient file of a retained selection: its chart (the head, for charts linked by Merge
     * Records), and the other charts of its group, ascending (none for a chart in no group).
     */
    @FunctionalInterface
    public interface RetainedFiles {
        void accept(CdsExportSelectionRowDto patient, java.util.List<Integer> members)
                throws IOException;
    }

    /**
     * Reads protected retained selection metadata after the caller authorizes its published
     * artifact context. Exact intent/epoch/count and authenticated EOF remain mandatory.
     * The callback receives identifiers, never a Patient capture capability or execution lease.
     */
    public long readRetained(
            CdsExportStagingService store,
            String runId,
            long epoch,
            CdsExportJobDocumentDto intent,
            CdsExportStagedFileDto file,
            long expectedPatients,
            Runnable checkpoint,
            RetainedPatients patients)
            throws IOException {
        Objects.requireNonNull(patients);
        return readRetained(
                store,
                runId,
                epoch,
                intent,
                file,
                expectedPatients,
                checkpoint,
                (patient, members) -> patients.accept(patient));
    }

    /**
     * {@link #readRetained(CdsExportStagingService, String, long, CdsExportJobDocumentDto,
     * CdsExportStagedFileDto, long, Runnable, RetainedPatients)} by patient file: a group of
     * charts linked by Merge Records is one file, named by its head, with its other charts.
     * {@code expectedPatients} is the number of patient files.
     */
    public long readRetained(
            CdsExportStagingService store,
            String runId,
            long epoch,
            CdsExportJobDocumentDto intent,
            CdsExportStagedFileDto file,
            long expectedPatients,
            Runnable checkpoint,
            RetainedFiles patients)
            throws IOException {
        outside();
        Objects.requireNonNull(store);
        Objects.requireNonNull(intent);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(patients);
        if (expectedPatients < 1) invalid();
        checkpoint.run();
        var manifest = manifest(store, file);
        if (!manifest.runId().equals(runId)
                || manifest.epoch() != epoch
                || !manifest.intent().equals(intent)
                || manifest.selection().patientFiles() != expectedPatients) invalid();
        var plan = plan(store, manifest.selection(), checkpoint);
        long count = 0;
        try (var input = store.open(manifest.selection().inventory())) {
            var files =
                    new Files(
                            new Rows(
                                    input,
                                    intent.command(),
                                    manifest.selection().patients(),
                                    checkpoint),
                            plan,
                            expectedPatients);
            Files.Entry entry;
            while ((entry = files.next()) != null) {
                checkpoint.run();
                patients.accept(
                        entry.row(),
                        entry.group() == null ? java.util.List.of() : entry.group().members());
                count++;
            }
            if (count != expectedPatients) invalid();
        }
        checkpoint.run();
        return count;
    }

    /**
     * The stored form of a manifest. One of at most {@link #ONE_LINE} bytes is its JSON, byte for
     * byte as before. A longer one is two lines, as {@code CdsExportLedgerService} stores a long
     * job document: the same JSON with the command's two selection lists empty and a {@code lists}
     * member giving the count of each list and the SHA-256 of the second line; then the second
     * line, {@code {"patientNos":[...],"physicianNos":[...]}} in the command's own sorted order.
     * The second line is written from the command as it streams, never held as text. The file's
     * own digest, which the ledger records and every patient scope names, covers both lines.
     */
    private static void write(CdsExportCohortManifestDto manifest, OutputStream output)
            throws IOException {
        var counted = new Counted(OutputStream.nullOutputStream());
        JSON.writeValue(counted, manifest);
        if (counted.count <= ONE_LINE) {
            output.write(JSON.writeValueAsBytes(manifest));
            return;
        }
        var command = manifest.intent().command();
        var digest = sha256();
        var length = new Counted(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
        lists(command, length);
        com.fasterxml.jackson.databind.node.ObjectNode head = JSON.valueToTree(manifest);
        var intent = (com.fasterxml.jackson.databind.node.ObjectNode) head.get("intent");
        var named = (com.fasterxml.jackson.databind.node.ObjectNode) intent.get("command");
        named.putArray("patientNos");
        named.putArray("physicianNos");
        head.putObject("lists")
                .put("patients", command.patientNos().size())
                .put("physicians", command.physicianNos().size())
                .put("sha256", HexFormat.of().formatHex(digest.digest()));
        byte[] first = JSON.writeValueAsBytes(head);
        // Everything left on the first line has a fixed greatest length; this cannot happen.
        boolean newline = false;
        for (byte value : first) newline |= value == '\n';
        if (newline || first.length > ONE_LINE)
            throw new IOException("CDS_EXPORT_COHORT_ENCODING_FAILED");
        output.write(first);
        output.write('\n');
        lists(command, output);
    }

    /** The second line: the command's lists, as the ledger writes them. */
    private static void lists(CdsExportCommand command, OutputStream output) throws IOException {
        var text = new ByteArrayOutputStream(8192);
        text.writeBytes("{\"patientNos\":[".getBytes(StandardCharsets.US_ASCII));
        String separator = "";
        for (Integer patient : command.patientNos()) {
            text.writeBytes((separator + patient.intValue()).getBytes(StandardCharsets.US_ASCII));
            separator = ",";
            if (text.size() >= 8192) {
                text.writeTo(output);
                text.reset();
            }
        }
        text.writeBytes("],\"physicianNos\":[".getBytes(StandardCharsets.US_ASCII));
        separator = "";
        // A provider number is letters, digits, "_" and "-" (CdsExportCommand): no escape.
        for (String physician : command.physicianNos()) {
            text.writeBytes(
                    (separator + '"' + physician + '"').getBytes(StandardCharsets.US_ASCII));
            separator = ",";
            if (text.size() >= 8192) {
                text.writeTo(output);
                text.reset();
            }
        }
        text.writeBytes("]}".getBytes(StandardCharsets.US_ASCII));
        text.writeTo(output);
    }

    /**
     * Reads a manifest back in either stored form. A two-line one is used only once its second
     * line is exactly the selection the first line names: its SHA-256, both counts and an
     * authenticated end of file.
     */
    private static CdsExportCohortManifestDto manifest(
            CdsExportStagingService store, CdsExportStagedFileDto file) throws IOException {
        if (!file.purpose().equals("COHORT_MANIFEST") || file.plaintextBytes() < 1) invalid();
        try (var input = new BufferedInputStream(store.open(file))) {
            var first = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1 && value != '\n') {
                if (first.size() >= ONE_LINE) invalid();
                first.write(value);
            }
            if (value == -1)
                return JSON.readValue(first.toByteArray(), CdsExportCohortManifestDto.class);
            var head = JSON.readTree(first.toByteArray());
            if (!(head instanceof com.fasterxml.jackson.databind.node.ObjectNode document)
                    || !(document.remove("lists")
                            instanceof com.fasterxml.jackson.databind.node.ObjectNode named)
                    || !(document.path("intent").get("command")
                            instanceof com.fasterxml.jackson.databind.node.ObjectNode command)
                    || named.size() != 3
                    || !named.path("patients").isInt()
                    || !named.path("physicians").isInt()
                    || !named.path("sha256").isTextual()
                    || !command.path("patientNos").isArray()
                    || !command.path("patientNos").isEmpty()
                    || !command.path("physicianNos").isArray()
                    || !command.path("physicianNos").isEmpty())
                throw new IOException("CDS_EXPORT_COHORT_INVENTORY_INVALID");
            var digest = sha256();
            var patients = JSON.getNodeFactory().arrayNode();
            var physicians = JSON.getNodeFactory().arrayNode();
            var rest = new DigestInputStream(input, digest);
            try (var parser =
                    JSON.getFactory()
                            .createParser(
                                    new FilterInputStream(rest) {
                                        @Override
                                        public void close() {
                                            // The authenticated file is closed by its owner.
                                        }
                                    })) {
                expect(parser, com.fasterxml.jackson.core.JsonToken.START_OBJECT);
                field(parser, "patientNos");
                expect(parser, com.fasterxml.jackson.core.JsonToken.START_ARRAY);
                for (var token = parser.nextToken();
                        token != com.fasterxml.jackson.core.JsonToken.END_ARRAY;
                        token = parser.nextToken()) {
                    if (token != com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT
                            || parser.getNumberType()
                                    != com.fasterxml.jackson.core.JsonParser.NumberType.INT)
                        invalid();
                    patients.add(parser.getIntValue());
                }
                field(parser, "physicianNos");
                expect(parser, com.fasterxml.jackson.core.JsonToken.START_ARRAY);
                for (var token = parser.nextToken();
                        token != com.fasterxml.jackson.core.JsonToken.END_ARRAY;
                        token = parser.nextToken()) {
                    if (token != com.fasterxml.jackson.core.JsonToken.VALUE_STRING) invalid();
                    physicians.add(parser.getText());
                }
                expect(parser, com.fasterxml.jackson.core.JsonToken.END_OBJECT);
                if (parser.nextToken() != null) invalid();
            } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
                invalid();
            }
            // Every byte after the first line is the second line, to the authenticated end.
            rest.transferTo(OutputStream.nullOutputStream());
            if (!HexFormat.of().formatHex(digest.digest()).equals(named.get("sha256").textValue())
                    || patients.size() != named.get("patients").intValue()
                    || physicians.size() != named.get("physicians").intValue()) invalid();
            command.set("patientNos", patients);
            command.set("physicianNos", physicians);
            return JSON.treeToValue(document, CdsExportCohortManifestDto.class);
        }
    }

    private static void expect(
            com.fasterxml.jackson.core.JsonParser parser, com.fasterxml.jackson.core.JsonToken token)
            throws IOException {
        if (parser.nextToken() != token) invalid();
    }

    private static void field(com.fasterxml.jackson.core.JsonParser parser, String name)
            throws IOException {
        if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.FIELD_NAME
                || !name.equals(parser.currentName())) invalid();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Counts what passes through, so a manifest's length is known before it is stored. */
    private static final class Counted extends java.io.FilterOutputStream {
        private long count;

        Counted(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            count += length;
        }

        @Override
        public void close() {
            // The counted stream is only measured; nothing is closed through it.
        }
    }

    private static void verifySelection(
            CdsExportStagingService store,
            CdsExportCommand command,
            CdsExportSelectionDto selection,
            CdsExportRunControl control)
            throws IOException {
        var plan = plan(store, selection, control::checkpoint);
        try (var input = store.open(selection.inventory())) {
            var files =
                    new Files(
                            new Rows(input, command, selection.patients(), control),
                            plan,
                            selection.patientFiles());
            while (files.next() != null) {
                /* Full authenticated EOF before any patient scope is issued. */
            }
        }
    }

    /**
     * The frozen group plan of a selection ({@link CdsExportMergeGroupPlanner}), read strictly:
     * heads ascending, every chart in one group only, and each group's selected charts among its
     * charts. A selection without a plan has no groups.
     */
    private static Plan plan(
            CdsExportStagingService store, CdsExportSelectionDto selection, Runnable checkpoint)
            throws IOException {
        if (selection.groups() == null) return Plan.NONE;
        var groups = new java.util.ArrayList<CdsExportMergeGroupDto>();
        var byChart = new java.util.HashMap<Integer, CdsExportMergeGroupDto>();
        long selected = 0;
        // Read as a stream of JSON objects, so no group is refused for its length.
        try (var input = store.open(selection.groups());
                var parser = JSON.getFactory().createParser(input)) {
            int previous = 0;
            com.fasterxml.jackson.core.JsonToken token;
            while ((token = parser.nextToken()) != null) {
                checkpoint.run();
                if (token != com.fasterxml.jackson.core.JsonToken.START_OBJECT) invalid();
                CdsExportMergeGroupDto group;
                try {
                    group = group(JSON.readTree(parser));
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("CDS_EXPORT_COHORT_INVENTORY_INVALID");
                }
                if (group.head() <= previous) invalid();
                for (int chart : group.charts()) if (byChart.put(chart, group) != null) invalid();
                groups.add(group);
                selected += group.selected().size();
                previous = group.head();
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            invalid();
        }
        long files = selection.patientFiles();
        // Every selected chart of a group is a selected chart: never more of them than the
        // selection lists, and the files are the lone charts plus the groups.
        if (selected > selection.patients()
                || files != selection.patients() - selected + groups.size()) invalid();
        return new Plan(groups, byChart);
    }

    /** One group line, exactly the members the planner writes, in its order. */
    private static CdsExportMergeGroupDto group(com.fasterxml.jackson.databind.JsonNode node)
            throws IOException {
        if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode)) invalid();
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
        var names = new java.util.ArrayList<String>();
        object.fieldNames().forEachRemaining(names::add);
        if (!names.equals(
                        java.util.List.of(
                                "head", "physicianNo", "members", "selected", "links", "shape"))
                || !object.path("head").isInt()
                || !(object.path("physicianNo").isNull() || object.path("physicianNo").isTextual()))
            invalid();
        return new CdsExportMergeGroupDto(
                object.path("head").intValue(),
                object.path("physicianNo").isNull() ? null : object.path("physicianNo").textValue(),
                integers(object.path("members")),
                integers(object.path("selected")),
                integers(object.path("links")),
                texts(object.path("shape")));
    }

    private static java.util.List<Integer> integers(com.fasterxml.jackson.databind.JsonNode node)
            throws IOException {
        if (!node.isArray()) invalid();
        var values = new java.util.ArrayList<Integer>(node.size());
        for (var value : node) {
            if (!value.isInt()) invalid();
            values.add(value.intValue());
        }
        return values;
    }

    private static java.util.List<String> texts(com.fasterxml.jackson.databind.JsonNode node)
            throws IOException {
        if (!node.isArray()) invalid();
        var values = new java.util.ArrayList<String>(node.size());
        for (var value : node) {
            if (!value.isTextual()) invalid();
            values.add(value.textValue());
        }
        return values;
    }

    /** A verified group plan: its groups by head, and the group of each of their charts. */
    private record Plan(
            java.util.List<CdsExportMergeGroupDto> groups,
            java.util.Map<Integer, CdsExportMergeGroupDto> byChart) {
        static final Plan NONE = new Plan(java.util.List.of(), java.util.Map.of());
    }

    /**
     * The patient files of a selection in ascending order of their chart: each selected chart in
     * no group, and each group by its head. A selected chart of a group is read past in the
     * selection and must be one of its group's selected charts; every one of them must be there.
     */
    private static final class Files {
        record Entry(CdsExportSelectionRowDto row, CdsExportMergeGroupDto group) {}

        private final Rows rows;
        private final Plan plan;
        private final long expected;
        private int next;
        private CdsExportSelectionRowDto pending;
        private long covered, coveredExpected, count;
        private boolean eof;

        Files(Rows rows, Plan plan, long expected) {
            this.rows = rows;
            this.plan = plan;
            this.expected = expected;
            for (var group : plan.groups()) coveredExpected += group.selected().size();
        }

        Entry next() throws IOException {
            if (eof) return null;
            if (pending == null) pending = lone();
            var group = next < plan.groups().size() ? plan.groups().get(next) : null;
            if (pending == null && group == null) {
                if (covered != coveredExpected || count != expected) invalid();
                eof = true;
                return null;
            }
            Entry entry;
            if (group != null && (pending == null || group.head() < pending.demographicNo())) {
                next++;
                entry = new Entry(new CdsExportSelectionRowDto(group.head(), group.physicianNo()), group);
            } else {
                if (group != null && group.head() == pending.demographicNo()) invalid();
                entry = new Entry(pending, null);
                pending = null;
            }
            if (++count > expected) invalid();
            return entry;
        }

        /** The next selected chart in no group. */
        private CdsExportSelectionRowDto lone() throws IOException {
            CdsExportSelectionRowDto row;
            while ((row = rows.next()) != null) {
                var group = plan.byChart().get(row.demographicNo());
                if (group == null) return row;
                if (java.util.Collections.binarySearch(group.selected(), row.demographicNo()) < 0)
                    invalid();
                covered++;
            }
            return null;
        }
    }

    public static final class Sealed {
        private final CdsExportLeaseDto lease;
        private final CdsExportCohortManifestDto manifest;
        private final CdsExportStagedFileDto member;

        private Sealed(
                CdsExportLeaseDto lease,
                CdsExportCohortManifestDto manifest,
                CdsExportStagedFileDto member) {
            this.lease = lease;
            this.manifest = manifest;
            this.member = member;
        }

        void require(CdsExportLeaseDto supplied) {
            if (!lease.equals(supplied))
                throw new IllegalStateException("CDS_EXPORT_COHORT_SCOPE_MISMATCH");
        }

        public CdsExportStagedFileDto member() {
            return member;
        }

        /** The number of patient files: charts linked by Merge Records are one file. */
        public long patients() {
            return manifest.selection().patientFiles();
        }

        public Instant readCompletedAt() {
            return manifest.selection().readCompletedAt();
        }

        @Override
        public String toString() {
            return "CdsExportSealedCohort[protected]";
        }
    }

    /**
     * The scope of one chart of the current patient file. A file of charts linked by Merge
     * Records ({@link CdsExportMergeGroupPlanner}) is opened on its head; {@link #member} gives a
     * scope for each other chart of the group, which expires with the head's.
     */
    public static final class Patient {
        private final Cursor cursor;
        private final long generation;
        private final CdsExportSelectionRowDto row;
        private final CdsExportMergeGroupDto group;
        private final int chart;

        private Patient(
                Cursor cursor,
                long generation,
                CdsExportSelectionRowDto row,
                CdsExportMergeGroupDto group,
                int chart) {
            this.cursor = cursor;
            this.generation = generation;
            this.row = row;
            this.group = group;
            this.chart = chart;
        }

        void require(CdsExportLeaseDto lease) {
            cursor.sealed.require(lease);
            if (cursor.closed
                    || cursor.failed
                    || cursor.generation.get() != generation
                    || Thread.currentThread() != cursor.owner)
                throw new IllegalStateException("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
        }

        /** The chart this scope reads. */
        public int patient() {
            return chart;
        }

        /**
         * The selection row of the patient file: the head and its physician when the job was
         * selected. A merged chart's scope has its own number and no physician.
         */
        public CdsExportSelectionRowDto selection() {
            return chart == row.demographicNo() ? row : new CdsExportSelectionRowDto(chart, null);
        }

        /** The chart of the patient file this scope belongs to. */
        public int head() {
            return row.demographicNo();
        }

        /** The group of the patient file, or null for a chart in no group. */
        public CdsExportMergeGroupDto group() {
            return group;
        }

        /** The patient file's other charts, ascending; none outside a group. */
        public java.util.List<Integer> members() {
            return group == null ? java.util.List.of() : group.members();
        }

        /**
         * A scope for one chart of this patient file, with the same lease, thread and cursor
         * position checks as this one.
         *
         * @throws IllegalArgumentException when the chart is not one of the file's
         */
        public Patient member(int other) {
            if (other == chart) return this;
            if (other == row.demographicNo()) return new Patient(cursor, generation, row, group, other);
            if (group == null || !group.contains(other))
                throw new IllegalArgumentException("CDS_EXPORT_PATIENT_SCOPE_INVALID");
            return new Patient(cursor, generation, row, group, other);
        }

        public String cohortSha256() {
            return cursor.sealed.member.plaintextSha256();
        }

        @Override
        public String toString() {
            return "CdsExportCohortPatient[protected]";
        }
    }

    public static final class Cursor implements AutoCloseable {
        private final Sealed sealed;
        private final InputStream input;
        private final Files files;
        private volatile boolean closed, failed;
        private final java.util.concurrent.atomic.AtomicLong generation =
                new java.util.concurrent.atomic.AtomicLong();
        private final Thread owner = Thread.currentThread();

        private Cursor(Sealed sealed, InputStream input, Plan plan, CdsExportRunControl control) {
            this.sealed = sealed;
            this.input = input;
            files =
                    new Files(
                            new Rows(
                                    input,
                                    sealed.lease.document().command(),
                                    sealed.manifest.selection().patients(),
                                    control),
                            plan,
                            sealed.patients());
        }

        /** The next patient file, opened on its chart (the head of a group), or null. */
        public Patient next() throws IOException {
            try {
                if (closed || failed || Thread.currentThread() != owner)
                    throw new IOException("CDS_EXPORT_COHORT_CURSOR_CLOSED");
                long current = generation.incrementAndGet();
                var entry = files.next();
                return entry == null
                        ? null
                        : new Patient(
                                this,
                                current,
                                entry.row(),
                                entry.group(),
                                entry.row().demographicNo());
            } catch (IOException | RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }

        public boolean complete() {
            return !failed && files.eof && files.rows.eof;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            generation.incrementAndGet();
            input.close();
        }

        @Override
        public String toString() {
            return "CdsExportCohortCursor[protected]";
        }
    }

    private static final class Rows {
        private final CdsExportJsonLines lines;
        private final CdsExportCommand command;
        private final long expected;
        private long count;
        private int previous;
        private boolean eof;

        Rows(
                InputStream input,
                CdsExportCommand command,
                long expected,
                CdsExportRunControl control) {
            this(input, command, expected, control::checkpoint);
        }

        Rows(InputStream input, CdsExportCommand command, long expected, Runnable checkpoint) {
            lines = new CdsExportJsonLines(input, 1024, checkpoint);
            this.command = command;
            this.expected = expected;
        }

        CdsExportSelectionRowDto next() throws IOException {
            if (eof) return null;
            var node = lines.next();
            if (node == null) {
                if (count != expected
                        || command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS
                                && count != command.patientNos().size()) invalid();
                eof = true;
                return null;
            }
            if (node.size() != 2
                    || !node.has("demographicNo")
                    || !node.has("physicianNo")
                    || count >= expected) invalid();
            var id = node.get("demographicNo");
            var physician = node.get("physicianNo");
            if (!id.isIntegralNumber()
                    || !id.canConvertToInt()
                    || id.intValue() < 1
                    || id.intValue() <= previous
                    || !(physician.isNull() || physician.isTextual())
                    || physician.isTextual()
                            && (physician.textValue().length() > 16
                                    || physician
                                            .textValue()
                                            .codePoints()
                                            .anyMatch(
                                                    cp ->
                                                            Character.isISOControl(cp)
                                                                    || cp >= 0xd800
                                                                            && cp <= 0xdfff)))
                invalid();
            int patient = id.intValue();
            String provider = physician.isNull() ? null : physician.textValue();
            if (command.selection() == CdsExportCommand.Selection.SELECTED_PATIENTS) {
                if (count >= command.patientNos().size()
                        || command.patientNos().get((int) count) != patient) invalid();
            } else if (provider == null
                    // The command keeps its physicians sorted: no scan of the list per patient.
                    || java.util.Collections.binarySearch(command.physicianNos(), provider) < 0)
                invalid();
            count++;
            previous = patient;
            return new CdsExportSelectionRowDto(patient, provider);
        }
    }

    private static void requireBound(CdsExportLeaseDto lease) throws IOException {
        if (lease == null || lease.document().protocol() != 2 || lease.document().storage() == null)
            throw new IOException("CDS_EXPORT_STORAGE_BINDING_REQUIRED");
    }

    private static void outside() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_COHORT_INVENTORY_INVALID");
    }
}
