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

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads one export attempt's chain of {@value CdsExportChartCheckpointDto#PURPOSE} members
 * ({@link CdsExportChartCheckpointPersister}) through that attempt's staging store, trusting it
 * only from the pointer the ledger recorded on the attempt row.
 *
 * <p>{@link #open} walks the chain from that pointer back to its first member: every member is
 * read to its authenticated end (its scope, run, epoch and purpose are bound into its key, and its
 * plaintext digest and length are checked), each member must name exactly the member before it,
 * the sequence, counts and patient order must agree from member to member, and the newest member
 * must count exactly the charts the pointer counts. A chain that fails any of these is refused as a
 * whole. {@link Chain#cursor} then reads the entries again, oldest first, each member again
 * checked to its end.
 *
 * <p>Memory: one descriptor for each member of the chain and one entry at a time.
 *
 * @since 2026-11-14
 */
final class CdsExportChartCheckpointReader {
    /** The checkpoint encoding: strict, with no duplicate member and no trailing data in a value. */
    static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .build())
                    .build();

    private CdsExportChartCheckpointReader() {}

    /** One entry of a chain, in cohort order. */
    record Entry(
            String kind, int patient, boolean reusable, String fingerprint, JsonNode chart, String code) {
        boolean chartEntry() {
            return "CHART".equals(kind);
        }

        @Override
        public String toString() {
            return "CdsExportChartCheckpointReader.Entry[protected]";
        }
    }

    /** A verified chain. */
    static final class Chain {
        private final CdsExportStagingService store;
        private final long epoch;
        private final String runId, parameters;
        private final long charts, held;
        private final int lastPatient;
        private final List<CdsExportStagedFileDto> members;
        private final Runnable checkpoint;

        private Chain(
                CdsExportStagingService store,
                String runId,
                long epoch,
                String parameters,
                long charts,
                long held,
                int lastPatient,
                List<CdsExportStagedFileDto> members,
                Runnable checkpoint) {
            this.store = store;
            this.runId = runId;
            this.epoch = epoch;
            this.parameters = parameters;
            this.charts = charts;
            this.held = held;
            this.lastPatient = lastPatient;
            this.members = List.copyOf(members);
            this.checkpoint = checkpoint;
        }

        /** The attempt the chain belongs to. */
        long epoch() {
            return epoch;
        }

        /** The SHA-256 of the build parameters its charts were made under. */
        String parameters() {
            return parameters;
        }

        long charts() {
            return charts;
        }

        long held() {
            return held;
        }

        /** The highest patient number the chain names; every lower one it reached is named too. */
        int lastPatient() {
            return lastPatient;
        }

        /** The chain's members, oldest first; each is pinned by the newest one. */
        List<CdsExportStagedFileDto> members() {
            return members;
        }

        /** The store the chain and the members its charts name live in. */
        CdsExportStagingService store() {
            return store;
        }

        /** The entries, oldest first. The caller closes the cursor. */
        Cursor cursor() {
            return new Cursor(this);
        }

        @Override
        public String toString() {
            return "CdsExportChartCheckpointReader.Chain[protected]";
        }
    }

    /** A forward read of a chain's entries; {@code null} is its authenticated end. */
    static final class Cursor implements AutoCloseable {
        private final Chain chain;
        private int member;
        private InputStream input;
        private MappingIterator<JsonNode> values;
        private int previous;
        private long entries;

        private Cursor(Chain chain) {
            this.chain = chain;
        }

        Entry next() throws IOException {
            while (true) {
                chain.checkpoint.run();
                if (values == null) {
                    if (member == chain.members.size()) return null;
                    input = chain.store.open(chain.members.get(member));
                    values = values(input);
                    var header = values.hasNextValue() ? values.nextValue() : null;
                    if (header == null
                            || !"CHECKPOINT".equals(header.path("kind").textValue())
                            || header.path("sequence").asLong(-1) != member + 1L) invalid();
                    entries = 0;
                }
                var node = values.hasNextValue() ? values.nextValue() : null;
                if (node == null) invalid();
                if ("END".equals(node.path("kind").textValue())) {
                    if (node.path("entries").asLong(-1) != entries || values.hasNextValue())
                        invalid();
                    // Read to the member's authenticated end before anything more is trusted.
                    if (input.read() != -1) invalid();
                    closeMember();
                    member++;
                    continue;
                }
                var entry = entry(node, previous);
                previous = entry.patient();
                entries++;
                return entry;
            }
        }

        /** Closes the parser and the member's stream; a second failure is suppressed on the first. */
        private void closeMember() throws IOException {
            var parser = values;
            var opened = input;
            values = null;
            input = null;
            IOException primary = null;
            try {
                if (parser != null) parser.close();
            } catch (IOException failure) {
                primary = failure;
            }
            try {
                if (opened != null) opened.close();
            } catch (IOException failure) {
                if (primary == null) primary = failure;
                else primary.addSuppressed(failure);
            }
            if (primary != null) throw primary;
        }

        @Override
        public void close() throws IOException {
            closeMember();
        }
    }

    /**
     * Verifies the chain whose newest member {@code pointer} names.
     *
     * @throws IOException {@code CDS_EXPORT_CHART_CHECKPOINT_INVALID} when the chain is not
     *     exactly what the pointer pins, or the staging store's own code when a member cannot be
     *     read or authenticated
     */
    static Chain open(
            CdsExportStagingService store,
            String runId,
            long epoch,
            CdsExportChartCheckpointDto pointer,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(store);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(pointer);
        Objects.requireNonNull(checkpoint);
        var newestFirst = new ArrayList<CdsExportStagedFileDto>();
        CdsExportStagedFileDto current = pointer.member();
        String parameters = null;
        long expectedCharts = pointer.charts(), expectedHeld = -1, sequence = -1;
        long totalHeld = -1;
        int expectedLast = -1, newestLast = -1;
        while (current != null) {
            checkpoint.run();
            var summary = summary(store, runId, epoch, current, checkpoint);
            if (parameters == null) parameters = summary.parameters;
            else if (!parameters.equals(summary.parameters)) invalid();
            if (sequence >= 0 && summary.sequence != sequence - 1) invalid();
            if (summary.endCharts != expectedCharts
                    || expectedHeld >= 0 && summary.endHeld != expectedHeld
                    || expectedLast >= 0 && summary.endLast != expectedLast) invalid();
            if (totalHeld < 0) {
                totalHeld = summary.endHeld;
                newestLast = summary.endLast;
            }
            newestFirst.add(current);
            sequence = summary.sequence;
            expectedCharts = summary.startCharts;
            expectedHeld = summary.startHeld;
            expectedLast = summary.startLast;
            current = summary.previous;
            if (current == null
                    && (sequence != 1
                            || summary.startCharts != 0
                            || summary.startHeld != 0
                            || summary.startLast != 0)) invalid();
        }
        return new Chain(
                store,
                runId,
                epoch,
                parameters,
                pointer.charts(),
                totalHeld,
                newestLast,
                newestFirst.reversed(),
                checkpoint);
    }

    private record Summary(
            long sequence,
            String parameters,
            CdsExportStagedFileDto previous,
            long startCharts,
            long startHeld,
            int startLast,
            long endCharts,
            long endHeld,
            int endLast) {}

    /** Reads one member whole and checks it on its own. */
    private static Summary summary(
            CdsExportStagingService store,
            String runId,
            long epoch,
            CdsExportStagedFileDto file,
            Runnable checkpoint)
            throws IOException {
        if (!CdsExportChartCheckpointDto.PURPOSE.equals(file.purpose())) invalid();
        try (var input = store.open(file);
                MappingIterator<JsonNode> values = values(input)) {
            var header = values.hasNextValue() ? values.nextValue() : null;
            if (header == null
                    || !header.isObject()
                    || header.size() != 10
                    || !"CHECKPOINT".equals(header.path("kind").textValue())
                    || header.path("protocol").asInt(-1) != CdsExportChartCheckpointPersister.PROTOCOL
                    || !runId.equals(header.path("runId").textValue())
                    || !header.path("epoch").canConvertToLong()
                    || header.path("epoch").longValue() != epoch
                    || !header.path("sequence").canConvertToLong()
                    || header.path("sequence").longValue() < 1
                    || !header.path("parameters").isTextual()
                    || !header.path("parameters").textValue().matches("[0-9a-f]{64}")
                    || !count(header.path("charts"))
                    || !count(header.path("held"))
                    || !patientOrZero(header.path("lastPatient"))) invalid();
            long sequence = header.path("sequence").longValue();
            var previousNode = header.path("previous");
            CdsExportStagedFileDto previous = null;
            if (!previousNode.isNull()) {
                if (!previousNode.isObject()
                        || previousNode.size() != 3
                        || !previousNode.path("memberId").isTextual()
                        || !previousNode.path("sha256").isTextual()
                        || !previousNode.path("bytes").canConvertToLong()) invalid();
                try {
                    previous =
                            new CdsExportChartCheckpointDto(
                                            previousNode.path("memberId").textValue(),
                                            previousNode.path("sha256").textValue(),
                                            previousNode.path("bytes").longValue(),
                                            0)
                                    .member();
                } catch (IllegalArgumentException malformed) {
                    invalid();
                }
            }
            if (previous == null != (sequence == 1)) invalid();
            long charts = header.path("charts").longValue(), held = header.path("held").longValue();
            int last = header.path("lastPatient").intValue(), first = last;
            long entries = 0;
            while (true) {
                checkpoint.run();
                var node = values.hasNextValue() ? values.nextValue() : null;
                if (node == null) invalid();
                if ("END".equals(node.path("kind").textValue())) {
                    if (node.size() != 5
                            || node.path("entries").asLong(-1) != entries
                            || entries < 1
                            || !count(node.path("charts"))
                            || !count(node.path("held"))
                            || node.path("charts").longValue() != charts
                            || node.path("held").longValue() != held
                            || node.path("lastPatient").asInt(-1) != last
                            || values.hasNextValue()) invalid();
                    break;
                }
                var entry = entry(node, last);
                last = entry.patient();
                if (entry.chartEntry()) charts = Math.addExact(charts, 1);
                else held = Math.addExact(held, 1);
                entries++;
            }
            // The member's authenticated end: its digest and length are checked here.
            if (input.read() != -1) invalid();
            return new Summary(
                    sequence,
                    header.path("parameters").textValue(),
                    previous,
                    header.path("charts").longValue(),
                    header.path("held").longValue(),
                    first,
                    charts,
                    held,
                    last);
        } catch (com.fasterxml.jackson.core.JacksonException malformed) {
            throw new IOException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
        }
    }

    /**
     * The JSON values of one member. The parser never closes the member's stream: the caller reads
     * it to its authenticated end after the last value, and closes it.
     */
    private static MappingIterator<JsonNode> values(InputStream input) throws IOException {
        var parser =
                JSON.getFactory()
                        .createParser(input)
                        .disable(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE);
        return JSON.readerFor(JsonNode.class).readValues(parser);
    }

    /** One CHART or HELD entry after {@code previous} in cohort order. */
    private static Entry entry(JsonNode node, int previous) throws IOException {
        if (!node.isObject() || !node.path("patient").canConvertToInt()) invalid();
        int patient = node.path("patient").intValue();
        if (patient < 1 || patient <= previous) invalid();
        String kind = node.path("kind").textValue();
        if ("CHART".equals(kind)) {
            var chart = node.path("chart");
            if (node.size() != 5
                    || !node.path("reusable").isBoolean()
                    || !node.path("fingerprint").isTextual()
                    || !node.path("fingerprint").textValue().matches("[0-9a-f]{64}")
                    || !chart.isObject()
                    || chart.path("patient").asInt(-1) != patient) invalid();
            return new Entry(
                    kind,
                    patient,
                    node.path("reusable").booleanValue(),
                    node.path("fingerprint").textValue(),
                    chart,
                    null);
        }
        if ("HELD".equals(kind)) {
            if (node.size() != 3
                    || !node.path("code").isTextual()
                    || !node.path("code").textValue().matches("CDS_EXPORT_[A-Z0-9_]{1,85}"))
                invalid();
            return new Entry(kind, patient, false, null, null, node.path("code").textValue());
        }
        invalid();
        return null;
    }

    /**
     * True when a failure to open or read a kept checkpoint, or a member it pins, means the
     * checkpoint cannot be trusted or used at all (its chain does not match the recorded pointer,
     * a member is missing, altered or of another attempt, or the attempt's key is gone), rather
     * than that the storage failed for now.
     */
    static boolean untrusted(IOException failure) {
        if (failure instanceof java.nio.file.NoSuchFileException) return true;
        String code = failure.getMessage();
        return code != null
                && (code.equals("CDS_EXPORT_CHART_CHECKPOINT_INVALID")
                        || code.startsWith("CDS_EXPORT_STAGED_")
                        || code.equals("CDS_EXPORT_STORAGE_KEY_CHANGED")
                        || code.equals("CDS_EXPORT_AES256_KEY_REQUIRED"));
    }

    private static boolean count(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0;
    }

    private static boolean patientOrZero(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
    }
}
