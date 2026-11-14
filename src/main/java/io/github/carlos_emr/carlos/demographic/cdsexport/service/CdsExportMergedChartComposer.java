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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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
     * @param record the chart's {@value CdsMergedChartContract#CHART_RECORD} record, or null when
     *     NewCategory is not selected and none is written
     * @param carried the chart's Demographics and native demographic occurrences, which are in its
     *     record (or, without NewCategory, not exported) instead of the file's sections
     */
    public record Member(
            int chart,
            SortedMap<CdsCategory, CdsMergedChartContract.Range> positions,
            CdsMergedChartContract.ChartRecord record,
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
        var positions = new ArrayList<TreeMap<CdsCategory, CdsMergedChartContract.Range>>();
        var standalone = new ArrayList<ByteArrayOutputStream>();
        var carried = new ArrayList<List<CdsExportIdentityDto>>();
        var prefixes = new ArrayList<byte[]>();
        var suffixes = new ArrayList<byte[]>();
        var inputs = new ArrayList<InputStream>();
        try {
            for (var part : parts) {
                checkpoint.run();
                if (part.xml == null || part.occurrences.isEmpty()) invalid();
                // The composer writes occurrences back to back, sections in order.
                for (int index = 1; index < part.occurrences.size(); index++) {
                    var previous = part.occurrences.get(index - 1);
                    var range = part.occurrences.get(index);
                    if (range.offset() != previous.offset() + previous.length()
                            || range.category().ordinal() < previous.category().ordinal())
                        invalid();
                }
                var input = new BufferedInputStream(store.open(part.xml), 65536);
                inputs.add(input);
                prefixes.add(exactly(input, part.occurrences.getFirst().offset(), checkpoint));
                positions.add(new TreeMap<>());
                standalone.add(new ByteArrayOutputStream());
                carried.add(new ArrayList<>());
            }
            output.write(prefixes.getFirst());
            int[] next = new int[parts.size()];
            for (var category : CdsCategory.values()) {
                for (int index = 0; index < parts.size(); index++) {
                    var part = parts.get(index);
                    long before = written.getOrDefault(category, 0L);
                    long copied = 0;
                    while (next[index] < part.occurrences.size()
                            && part.occurrences.get(next[index]).category() == category) {
                        checkpoint.run();
                        var range = part.occurrences.get(next[index]++);
                        var input = inputs.get(index);
                        // A merged chart's own demographic records go into its standalone
                        // document; everything else of it is the file's.
                        byte[] start =
                                exactly(
                                        input,
                                        Math.min(range.length(), NATIVE_DEMOGRAPHIC.length),
                                        checkpoint);
                        boolean demographic =
                                index > 0
                                        && (category == CdsCategory.DEMOGRAPHICS
                                                || category == CdsCategory.NEW_CATEGORY
                                                        && java.util.Arrays.equals(
                                                                start, NATIVE_DEMOGRAPHIC));
                        OutputStream target =
                                demographic
                                        ? (records ? standalone.get(index) : OutputStream.nullOutputStream())
                                        : output;
                        copy(input, start, range, target, checkpoint);
                        if (!demographic) copied++;
                        else carried.get(index).add(range.identity());
                    }
                    if (copied > 0) {
                        written.put(category, before + copied);
                        if (index > 0)
                            positions
                                    .get(index)
                                    .put(category, new CdsMergedChartContract.Range(before + 1, copied));
                    }
                }
            }
            for (int index = 0; index < parts.size(); index++) {
                if (next[index] != parts.get(index).occurrences.size()) invalid();
                // To the authenticated end of the document.
                suffixes.add(inputs.get(index).readAllBytes());
            }
            var members = new ArrayList<Member>();
            for (int index = 1; index < parts.size(); index++) {
                CdsMergedChartContract.ChartRecord record = null;
                if (records) {
                    var document = new ByteArrayOutputStream();
                    document.write(prefixes.get(index));
                    standalone.get(index).writeTo(document);
                    document.write(suffixes.get(index));
                    byte[] bytes = document.toByteArray();
                    record =
                            new CdsMergedChartContract.ChartRecord(
                                    parts.get(index).chart,
                                    head,
                                    positions.get(index),
                                    utf8(bytes),
                                    CdsMergedChartContract.sha256(bytes));
                    // A standalone CDS file of the chart's own Demographics, checked before it
                    // is written.
                    try {
                        CdsMergedChartContract.verifyDocument(record);
                    } catch (IllegalArgumentException malformed) {
                        throw new IOException("CDS_EXPORT_MERGED_CHART_DOCUMENT_INVALID");
                    }
                }
                members.add(
                        new Member(
                                parts.get(index).chart,
                                positions.get(index),
                                record,
                                List.copyOf(carried.get(index))));
            }
            if (records) {
                long carriers = 0;
                for (var member : members) {
                    output.write(CdsExportMergedChartMapper.chartOccurrence(member.record()));
                    carriers++;
                }
                if (links != null) {
                    output.write(CdsExportMergedChartMapper.linksOccurrence(links));
                    carriers++;
                }
                if (carriers > 0) written.merge(CdsCategory.NEW_CATEGORY, carriers, Long::sum);
            }
            output.write(suffixes.getFirst());
            checkpoint.run();
            return new Result(written, members);
        } finally {
            IOException failure = null;
            for (var input : inputs)
                try {
                    input.close();
                } catch (IOException cleanup) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            if (failure != null) throw failure;
        }
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
        if (!HexFormat.of().formatHex(digest.digest()).equals(range.sha256()))
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

    private static String utf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException malformed) {
            throw new IOException("CDS_EXPORT_MERGED_CHART_DOCUMENT_INVALID");
        }
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
