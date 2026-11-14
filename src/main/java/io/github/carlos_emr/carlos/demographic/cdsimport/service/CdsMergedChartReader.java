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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds and checks the two records a CARLOS sender writes for charts linked by Merge Records
 * ({@link CdsMergedChartContract}): one {@code CARLOS.MergedChart.v1} per chart merged into the
 * file's chart, and one {@code CARLOS.MergeLinks.v1} with the file's links and privilege rows.
 *
 * <p>The importer imports every record of a patient file onto one patient, so a merged chart's
 * records land on the file's chart as they are; nothing is rebuilt as a separate chart and no link
 * is restored. Both records are kept as source only, like the other CARLOS records the importer
 * does not restore. They are checked from the file's own sealed parse before anything of the
 * patient is planned: the file's chart is their head, each merged chart is named once in the
 * sender identifier form and is not the head, its ranges lie inside their sections without
 * overlapping another's, and its standalone document has the SHA-256 it states, is schema-valid
 * and is the chart's own. A record that fails any check refuses the patient for review ({@value
 * #INVALID}); the rest of the run goes on.
 */
final class CdsMergedChartReader {
    /** The run's report code of a patient refused for a merged-chart record that failed its check. */
    static final String INVALID = "CDS_MERGED_CHART_CARRIER_INVALID";

    /** The run's report code naming how many merged charts a patient's file brought. */
    static final String IMPORTED = "CDS_MERGED_CHARTS_IMPORTED";

    private final CdsFieldParser fields;

    CdsMergedChartReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /**
     * The checked records of one patient file.
     *
     * @param charts each merged chart's record
     * @param links the file's links record, or null when it has none
     */
    record Carriers(List<CdsMergedChartContract.ChartRecord> charts, CdsMergedChartContract.LinksRecord links) {
        Carriers {
            charts = List.copyOf(charts);
        }

        /** How many charts were merged into the file's chart. */
        int merged() {
            return charts.size();
        }

        @Override
        public String toString() {
            return "CdsMergedChartReader.Carriers[protected]";
        }
    }

    /** A merged-chart record of the file failed its check: the patient is refused for review. */
    static final class InvalidCarrier extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidCarrier() {
            super(INVALID, null, false, false);
        }
    }

    /**
     * Reads and checks the file's merged-chart records.
     *
     * @param manifest the patient file's parsed records manifest
     * @param senderId the file's own sender identifier (its Demographics UniqueVendorIdSequence)
     * @return the records; none when the file has no merged-chart record
     * @throws InvalidCarrier when a record fails a check
     * @throws CdsImportException when the manifest is not the one its parse sealed
     */
    Carriers read(Path manifest, String senderId) throws IOException, InvalidCarrier {
        verifySeal(manifest);
        var sections = new EnumMap<CdsCategory, Long>(CdsCategory.class);
        var chartRecords = new ArrayList<CdsMergedChartContract.ChartRecord>();
        var linkRecords = new ArrayList<CdsMergedChartContract.LinksRecord>();
        boolean[] invalid = {false};
        try {
            fields.forEachRecord(
                    manifest,
                    record -> {
                        if (CdsCategory.wireNames().contains(record.section()))
                            sections.merge(
                                    CdsCategory.fromWireName(record.section()), 1L, Long::sum);
                        if (!record.section().equals("NewCategory") || invalid[0]) return;
                        try {
                            String category;
                            try {
                                category = fields.firstToken(record, "CategoryName", 64);
                            } catch (CdsInputException capacity) {
                                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED"))
                                    throw capacity;
                                return; // A longer name cannot equal either record's name.
                            }
                            if (CdsMergedChartContract.CHART_RECORD.equals(category))
                                chartRecords.add(
                                        CdsMergedChartContract.chartRecord(
                                                content(record, category)));
                            else if (CdsMergedChartContract.LINKS_RECORD.equals(category))
                                linkRecords.add(
                                        CdsMergedChartContract.linksRecord(
                                                content(record, category)));
                        } catch (IllegalArgumentException malformed) {
                            invalid[0] = true;
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        if (invalid[0] || linkRecords.size() > 1) throw new InvalidCarrier();
        if (chartRecords.isEmpty() && linkRecords.isEmpty()) return new Carriers(List.of(), null);
        try {
            int head = CdsMergedChartContract.chart(senderId);
            var members = new TreeSet<Integer>();
            var positions = new ArrayList<java.util.SortedMap<CdsCategory, CdsMergedChartContract.Range>>();
            for (var record : chartRecords) {
                if (record.head() != head || record.chart() == head || !members.add(record.chart()))
                    throw new InvalidCarrier();
                positions.add(record.positions());
                // The chart's own schema-valid standalone document, with the digest it states.
                CdsMergedChartContract.verifyDocument(record);
            }
            CdsMergedChartContract.requirePositions(sections, positions);
            var links = linkRecords.isEmpty() ? null : linkRecords.getFirst();
            // A file of merged charts always has its links; the links name exactly its charts.
            if (!members.isEmpty() && links == null) throw new InvalidCarrier();
            if (links != null) {
                var charts = new ArrayList<Integer>();
                charts.add(head);
                charts.addAll(members);
                if (!links.charts().equals(charts)) throw new InvalidCarrier();
            }
            return new Carriers(chartRecords, links);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCarrier();
        }
    }

    /** The manifest is the one its parse sealed, when the parse left a seal beside it. */
    private static void verifySeal(Path manifest) throws IOException {
        Path folder = manifest.toAbsolutePath().getParent();
        if (folder == null) return;
        Path seal = folder.resolve("parse.seal");
        if (!Files.isRegularFile(seal, LinkOption.NOFOLLOW_LINKS)) return;
        String sealed =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(CdsImportJobService.boundedFile(seal, 65536))
                        .path("recordsSha256")
                        .asText();
        MessageDigest digest = CdsPaths.sha256();
        try (InputStream input = Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        if (!CdsPaths.hex(digest.digest()).equals(sealed))
            throw new CdsImportException("PARSED_METADATA_HASH_MISMATCH");
    }

    /**
     * The whole content of the record's one data element of its own name, a string: the record's
     * JSON. A record whose element is missing, repeated or not a string fails its check.
     */
    private String content(CdsRecordDto record, String name) throws IOException {
        var groups = new HashMap<String, Map<String, CdsFieldDto>>();
        try {
            fields.forEachField(
                    record,
                    field -> {
                        if (!Set.of("cds", "cds_dt").contains(field.namespace())) return;
                        int slash = field.path().lastIndexOf('/');
                        if (slash < 0) return;
                        String parent = field.path().substring(0, slash);
                        if (!CdsFieldParser.unindexed(parent).equals("ResidualInfo/DataElement"))
                            return;
                        String leaf = CdsFieldParser.unindexed(field.path().substring(slash + 1));
                        if (!Set.of("Name", "DataType", "Content").contains(leaf)) return;
                        if (!field.kind().equals("TEXT")
                                || groups.computeIfAbsent(parent, ignored -> new HashMap<>())
                                                .putIfAbsent(leaf, field)
                                        != null)
                            throw new IllegalArgumentException(INVALID);
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        CdsFieldDto content = null;
        var seen = new HashSet<String>();
        for (var group : groups.values()) {
            var descriptor = group.get("Name");
            if (descriptor == null) continue;
            String element;
            try {
                element = fields.readToken(record, descriptor, 64);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                continue;
            }
            if (!name.equals(element)) continue;
            if (!seen.add(element)
                    || !group.keySet().equals(Set.of("Name", "DataType", "Content"))
                    || !CdsNativeExtensionContract.STRING_TYPE.equals(
                            fields.readToken(record, group.get("DataType"), 7)))
                throw new IllegalArgumentException(INVALID);
            content = group.get("Content");
        }
        if (content == null) throw new IllegalArgumentException(INVALID);
        // The record holds one standalone demographic document: read whole, whatever its length.
        var text = new StringBuilder();
        try (Reader reader = fields.openToken(record, content)) {
            char[] buffer = new char[8192];
            for (int read; (read = reader.read(buffer)) >= 0; ) text.append(buffer, 0, read);
        }
        return text.toString();
    }
}
