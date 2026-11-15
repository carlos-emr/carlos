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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Writes the patient file of a group of charts linked by Merge Records from each chart's own
 * private document ({@link CdsMergedChartContract}).
 *
 * <p>Each chart's document is first composed and validated as if the chart were exported alone,
 * and the composer records where each of its occurrences is ({@link CdsExportXmlComposer
 * .Occurrences}). The file then takes the head's declaration and root, and for each section in
 * order copies the head's occurrences, then each merged chart's, by byte range; every copied
 * range must have the SHA-256 of that occurrence's EMITTED disposition. A merged chart's
 * Demographics and {@code CARLOS.NativeDemographic.v1} records are not copied: they go into its
 * standalone document, so the file has one patient and the receiver's last native demographic
 * record is the head's. The NewCategory section ends with one {@value
 * CdsMergedChartContract#CHART_RECORD} record per merged chart and the {@value
 * CdsMergedChartContract#LINKS_RECORD} record. Without NewCategory no record is written and the
 * merged charts' Demographics are left out (an operational decision the ReadMe states).
 */
public final class CdsExportMergedChartComposer {
    /** How a member's native demographic record begins, as the composer writes it. */
    private static final byte[] NATIVE_DEMOGRAPHIC =
            ("<NewCategory><CategoryName>"
                            + CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY
                            + "</CategoryName>")
                    .getBytes(StandardCharsets.UTF_8);

    /** One occurrence of a chart's document. */
    public record Range(
            CdsCategory category,
            CdsExportIdentityDto identity,
            long offset,
            long length,
            String sha256) {
        public Range {
            Objects.requireNonNull(category);
            if (offset < 0 || length < 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("CDS_EXPORT_MERGED_CHART_RANGE_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportMergedChartComposer.Range[protected]";
        }
    }

    /** One chart's private document and where its occurrences are, in document order. */
    public static final class Part {
        private final int chart;
        private final List<Range> occurrences = new ArrayList<>();
        private CdsExportStagedFileDto xml;

        public Part(int chart) {
            if (chart < 1) throw new IllegalArgumentException("CDS_EXPORT_MERGED_CHART_INVALID");
            this.chart = chart;
        }

        /** Receives the chart document's occurrences as the XML composer writes them. */
        public CdsExportXmlComposer.Occurrences listener() {
            return (category, identity, offset, length, sha256) ->
                    occurrences.add(new Range(category, identity, offset, length, sha256));
        }

        /** The chart's finished, validated document. */
        public Part xml(CdsExportStagedFileDto document) {
            if (xml != null || !"PATIENT_XML".equals(document.purpose()))
                throw new IllegalStateException("CDS_EXPORT_MERGED_CHART_INVALID");
            xml = document;
            return this;
        }

        public int chart() {
            return chart;
        }

        @Override
        public String toString() {
            return "CdsExportMergedChartComposer.Part[protected]";
        }
    }

    /**
     * Where a merged chart's records are in the file, and its record.
     *
     * @param positions per section, the chart's first position and count (NewCategory included)
     * @param carrierWritten whether this chart's demographic carrier was written; its document
     *     is streamed from protected storage and is not retained in the result
     * @param carried the chart's Demographics and native demographic occurrences, which are in its
     *     record (or, without NewCategory, not exported) instead of the file's sections
     */
    public record Member(
            int chart,
            SortedMap<CdsCategory, CdsMergedChartContract.Range> positions,
            boolean carrierWritten,
            List<CdsExportIdentityDto> carried) {
        @Override
        public String toString() {
            return "CdsExportMergedChartComposer.Member[protected]";
        }
    }

    /**
     * The file's occurrences per section, and each merged chart's place in it, the head's
     * excluded.
     */
    public record Result(Map<CdsCategory, Long> occurrences, List<Member> members) {
        public Result {
            occurrences = Map.copyOf(occurrences);
            members = List.copyOf(members);
        }

        @Override
        public String toString() {
            return "CdsExportMergedChartComposer.Result[protected]";
        }
    }

    /**
     * Writes the file into {@code output}.
     *
     * @param parts every chart's document, the head first
     * @param links the file's links record, or null when no chart of it has a link or privilege
     *     row
     * @param records whether NewCategory is selected, so the records are written
     */
    // Cleanup throws only when composition succeeded; otherwise it is suppressed on the original
    // failure. A failed spool deletion must never turn into a successful published package.
    @SuppressWarnings("java:S1143")
    public Result compose(
            CdsExportStagingService store,
            OutputStream output,
            List<Part> parts,
            CdsMergedChartContract.LinksRecord links,
            boolean records,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(store);
        Objects.requireNonNull(output);
        Objects.requireNonNull(checkpoint);
        if (parts.isEmpty()) invalid();
        int head = parts.getFirst().chart;
        var written = new EnumMap<CdsCategory, Long>(CdsCategory.class);
        var sections = new EnumMap<CdsCategory, BufferedSpool>(CdsCategory.class);
        var documents = new ArrayList<CdsExportStagedFileDto>();
        var members = new ArrayList<Member>();
        byte[] headPrefix = null, headSuffix = null;
        Throwable primary = null;
        try {
            // One authenticated source is open at a time. Section spools preserve head/member
            // order with fixed buffers; no document-sized array or per-member live stream remains.
            for (int index = 0; index < parts.size(); index++) {
                checkpoint.run();
                var part = parts.get(index);
                if (part.xml == null || part.occurrences.isEmpty()) invalid();
                for (int occurrence = 1; occurrence < part.occurrences.size(); occurrence++) {
                    var previous = part.occurrences.get(occurrence - 1);
                    var range = part.occurrences.get(occurrence);
                    if (range.offset() != Math.addExact(previous.offset(), previous.length())
                            || range.category().ordinal() < previous.category().ordinal()) invalid();
                }
                var positions = new TreeMap<CdsCategory, CdsMergedChartContract.Range>();
                var copied = new EnumMap<CdsCategory, Long>(CdsCategory.class);
                var carried = new ArrayList<CdsExportIdentityDto>();
                try (var input = new BufferedInputStream(store.open(part.xml), 65536);
                        var demographic = new BufferedSpool(store, checkpoint)) {
                    byte[] prefix = exactly(input, part.occurrences.getFirst().offset(), checkpoint);
                    for (var range : part.occurrences) {
                        checkpoint.run();
                        byte[] start = exactly(input, Math.min(range.length(), NATIVE_DEMOGRAPHIC.length), checkpoint);
                        boolean nativeDemographic = index > 0 && (range.category() == CdsCategory.DEMOGRAPHICS
                                || range.category() == CdsCategory.NEW_CATEGORY && java.util.Arrays.equals(start, NATIVE_DEMOGRAPHIC));
                        OutputStream target;
                        if (nativeDemographic) {
                            target = records ? demographic : OutputStream.nullOutputStream();
                            carried.add(range.identity());
                        } else {
                            var spool = sections.get(range.category());
                            if (spool == null) {
                                spool = new BufferedSpool(store, checkpoint);
                                sections.put(range.category(), spool);
                            }
                            target = spool;
                            copied.merge(range.category(), 1L, Math::addExact);
                        }
                        copy(input, start, range, target, checkpoint);
                    }
                    // The source was already validated and its occurrence ranges cover every
                    // section; the remaining bytes are only its closing envelope, through EOF.
                    byte[] suffix = input.readAllBytes();
                    for (var entry : copied.entrySet()) {
                        long before = written.getOrDefault(entry.getKey(), 0L);
                        if (index > 0) positions.put(entry.getKey(), new CdsMergedChartContract.Range(before + 1, entry.getValue()));
                        written.put(entry.getKey(), Math.addExact(before, entry.getValue()));
                    }
                    if (index == 0) {
                        headPrefix = prefix;
                        headSuffix = suffix;
                    } else {
                        CdsExportStagedFileDto document = null;
                        if (records) {
                            document = store.stage("MERGED_DEMOGRAPHIC", targetDocument -> {
                                targetDocument.write(prefix);
                                demographic.transferTo(targetDocument);
                                targetDocument.write(suffix);
                            });
                            documents.add(document);
                            try (var check = store.open(document)) {
                                CdsMergedChartContract.verifyDocument(part.chart, check, document.plaintextSha256());
                            } catch (IllegalArgumentException malformed) {
                                throw new IOException("CDS_EXPORT_MERGED_CHART_DOCUMENT_INVALID");
                            }
                        }
                        members.add(new Member(part.chart, positions, records, List.copyOf(carried)));
                    }
                }
            }
            output.write(Objects.requireNonNull(headPrefix));
            for (var section : sections.values()) section.transferTo(output);
            if (records) {
                for (int index = 0; index < members.size(); index++) {
                    var member = members.get(index);
                    CdsExportMergedChartMapper.writeChartOccurrence(output, member.chart(), head,
                            member.positions(), documents.get(index), store, checkpoint);
                }
                long carriers = members.size();
                if (links != null) {
                    output.write(CdsExportMergedChartMapper.linksOccurrence(links));
                    carriers++;
                }
                if (carriers > 0) written.merge(CdsCategory.NEW_CATEGORY, carriers, Math::addExact);
            }
            output.write(Objects.requireNonNull(headSuffix));
            checkpoint.run();
            return new Result(written, members);
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            IOException cleanup = null;
            for (var spool : sections.values()) {
                try { spool.close(); }
                catch (IOException failure) {
                    if (cleanup == null) cleanup = failure; else cleanup.addSuppressed(failure);
                }
            }
            for (var document : documents) {
                try { store.discardUnpublished(document); }
                catch (IOException failure) {
                    if (cleanup == null) cleanup = failure; else cleanup.addSuppressed(failure);
                }
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup); else throw cleanup;
            }
        }
    }

    /** Fixed-size plaintext buffers; overflow goes to the attempt's encrypted, authenticated spool. */
    private static final class BufferedSpool extends OutputStream {
        private final CdsExportSpoolService spool;
        private final byte[] buffer = new byte[65536];
        private int used;
        private long chunks;

        BufferedSpool(CdsExportStagingService store, Runnable checkpoint) {
            spool = new CdsExportSpoolService(store, "MERGED_SECTION", checkpoint, 65536, 16);
        }

        @Override public void write(int value) throws IOException {
            if (used == buffer.length) flush();
            buffer[used++] = (byte) value;
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            while (length > 0) {
                if (used == buffer.length) flush();
                int count = Math.min(length, buffer.length - used);
                System.arraycopy(bytes, offset, buffer, used, count);
                used += count; offset += count; length -= count;
            }
        }

        @Override public void flush() throws IOException {
            if (used == 0) return;
            spool.append(Long.toString(chunks++), java.util.Arrays.copyOf(buffer, used));
            used = 0;
        }

        void transferTo(OutputStream output) throws IOException {
            flush();
            try (var cursor = spool.open()) {
                for (var entry = cursor.next(); entry != null; entry = cursor.next()) output.write(entry.value());
            }
        }

        @Override public void close() throws IOException { spool.close(); }
    }

    /** Copies one occurrence whose first bytes were read, checking its SHA-256. */
    private static void copy(
            InputStream input,
            byte[] start,
            Range range,
            OutputStream target,
            Runnable checkpoint)
            throws IOException {
        MessageDigest digest = sha256();
        digest.update(start);
        target.write(start);
        long left = range.length() - start.length;
        byte[] buffer = new byte[65536];
        while (left > 0) {
            checkpoint.run();
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) invalid();
            digest.update(buffer, 0, read);
            target.write(buffer, 0, read);
            left -= read;
        }
        // Exactly the bytes the chart's EMITTED disposition was computed from.
        if (!MessageDigest.isEqual(HexFormat.of().formatHex(digest.digest()).getBytes(StandardCharsets.US_ASCII),
                range.sha256().getBytes(StandardCharsets.US_ASCII)))
            throw new IOException("CDS_EXPORT_MERGED_CHART_COPY_MISMATCH");
    }

    private static byte[] exactly(InputStream input, long length, Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        if (length > Integer.MAX_VALUE - 8) invalid();
        byte[] bytes = input.readNBytes((int) length);
        if (bytes.length != length) invalid();
        return bytes;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_MERGED_CHART_INVALID");
    }
}
