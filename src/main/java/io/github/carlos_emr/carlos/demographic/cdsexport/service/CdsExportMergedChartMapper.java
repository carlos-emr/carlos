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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMergeGroupDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

/**
 * The two NewCategory records of a patient file of charts linked by Merge Records
 * ({@link CdsMergedChartContract}): the file's links and privilege rows, exactly as captured,
 * and each merged chart's record, as XML occurrences the merged file is assembled with.
 *
 * <p>Each chart's capture holds the links naming it (incoming, outgoing, deleted) and its
 * privilege row; the file's record holds every one of them once, in {@code id} order, a link
 * naming a chart outside the file marked so. Every column is carried as stored, so every field of
 * these rows is mapped ({@value CdsMergedChartContract#LINK_RULE}).
 */
public final class CdsExportMergedChartMapper {
    private CdsExportMergedChartMapper() {}

    /** The columns of a captured link row. */
    static final Set<String> LINK_COLUMNS =
            Set.of("id", "demographic_no", "merged_to", "deleted", "lastUpdateUser", "lastUpdateDate");

    /**
     * The file's links and privilege rows, from every chart's captured rows.
     *
     * @param links each chart's captured {@code demographic_merged} rows, by chart, the head first
     * @param privileges each chart's captured {@code merge_privileges} rows, by chart
     * @param group the frozen group of the file, or null for a chart that had no active link
     *     when the job was selected
     * @return the record, and how many links are active, deleted and name a chart outside the file
     */
    static Links links(
            List<Integer> charts,
            Map<Integer, List<CdsExportCapturedTextRowDto>> links,
            Map<Integer, List<CdsExportCapturedTextRowDto>> privileges,
            CdsExportMergeGroupDto group)
            throws IOException {
        var inFile = Set.copyOf(charts);
        var byId = new TreeMap<Long, CdsMergedChartContract.LinkRow>();
        var active = new TreeSet<Long>();
        var outgoing = new TreeMap<Integer, Integer>();
        boolean selfLink = false;
        for (int chart : charts)
            for (var row : links.getOrDefault(chart, List.of())) {
                var fields = row.fields();
                if (!"demographic_merged".equals(row.table())
                        || !fields.keySet().equals(LINK_COLUMNS)) invalid();
                long id = number(fields.get("id"));
                int from = (int) number(fields.get("demographic_no"));
                int to = (int) number(fields.get("merged_to"));
                if (from != chart && to != chart) invalid();
                var link =
                        new CdsMergedChartContract.LinkRow(
                                fields.get("id"),
                                fields.get("demographic_no"),
                                fields.get("merged_to"),
                                fields.get("deleted"),
                                fields.get("lastUpdateUser"),
                                fields.get("lastUpdateDate"),
                                !inFile.contains(from) || !inFile.contains(to));
                var prior = byId.putIfAbsent(id, link);
                // One snapshot: a link read from both of its charts is the same row.
                if (prior != null && !prior.equals(link)) invalid();
                if (prior == null && "0".equals(fields.get("deleted"))) {
                    active.add(id);
                    if (inFile.contains(from)) outgoing.merge(from, 1, Integer::sum);
                    selfLink |= from == to;
                }
            }
        var rows = new ArrayList<CdsMergedChartContract.PrivilegeRow>();
        for (int chart : new TreeSet<>(charts)) {
            var found = privileges.getOrDefault(chart, List.of());
            for (var row : found) {
                var fields = row.fields();
                if (!CdsExportMergePrivilegeSourceReader.TABLE.equals(row.table())
                        || !fields.keySet().equals(CdsExportMergePrivilegeSourceReader.COLUMNS))
                    invalid();
                rows.add(
                        new CdsMergedChartContract.PrivilegeRow(
                                chart,
                                fields.get("roleUserGroup"),
                                fields.get("objectName"),
                                fields.get("privilege"),
                                fields.get("priority"),
                                fields.get("provider_no")));
            }
            // A chart has at most one such row: the table's key is the role and the object.
            if (found.size() > 1) invalid();
        }
        var shape = new TreeSet<CdsMergedChartContract.Shape>();
        if (group != null)
            for (String value : group.shape()) shape.add(CdsMergedChartContract.Shape.valueOf(value));
        else {
            // A chart that had no active link when it was selected: only what its own rows show.
            if (selfLink) shape.add(CdsMergedChartContract.Shape.SELF_LINK);
            if (outgoing.values().stream().anyMatch(count -> count > 1))
                shape.add(CdsMergedChartContract.Shape.FORK);
        }
        var frozen = new TreeSet<Long>();
        if (group != null) for (int id : group.links()) frozen.add((long) id);
        SortedSet<String> changes = new TreeSet<>();
        if (!frozen.equals(active)) changes.add(CdsMergedChartContract.CHANGED_AFTER_SELECTION);
        long outside = byId.values().stream().filter(CdsMergedChartContract.LinkRow::outside).count();
        var record =
                new CdsMergedChartContract.LinksRecord(
                        charts, List.copyOf(byId.values()), rows, shape, changes);
        return new Links(record, active.size(), byId.size() - active.size(), outside);
    }

    /**
     * The file's links record and its counts.
     *
     * @param active links whose {@code deleted} is 0
     * @param deleted every other link
     * @param outside links naming a chart that is not in the file
     */
    record Links(
            CdsMergedChartContract.LinksRecord record, long active, long deleted, long outside) {
        /** Whether the file has any link or privilege row, and so a links record. */
        boolean any() {
            return !record.links().isEmpty() || !record.privileges().isEmpty();
        }
    }

    /** The NewCategory occurrence of one merged chart's record. */
    static byte[] chartOccurrence(CdsMergedChartContract.ChartRecord record) throws IOException {
        return occurrence(
                CdsMergedChartContract.CHART_RECORD,
                CdsMergedChartContract.CHART_DESCRIPTION,
                CdsMergedChartContract.json(record));
    }

    /** The NewCategory occurrence of the file's links record. */
    static byte[] linksOccurrence(CdsMergedChartContract.LinksRecord record) throws IOException {
        return occurrence(
                CdsMergedChartContract.LINKS_RECORD,
                CdsMergedChartContract.LINKS_DESCRIPTION,
                CdsMergedChartContract.json(record));
    }

    /**
     * One NewCategory element, written as the patient file's other elements are: in the default
     * namespace {@code cds}, its data element in {@code cds_dt} under the prefix the file's root
     * declares. The JSON is the content of a string data element, every space and every character
     * XML cannot carry written as its escape, so token normalization cannot change it.
     */
    private static byte[] occurrence(String name, String description, String json)
            throws IOException {
        var output = new ByteArrayOutputStream(json.length() + 512);
        try {
            var xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(output, "UTF-8");
            xml.setDefaultNamespace("cds");
            xml.setPrefix("cdsd", "cds_dt");
            xml.writeStartElement("", "NewCategory", "cds");
            element(xml, "", "CategoryName", "cds", name);
            element(xml, "", "CategoryDescription", "cds", description);
            xml.writeStartElement("", "ResidualInfo", "cds");
            xml.writeStartElement("cdsd", "DataElement", "cds_dt");
            element(xml, "cdsd", "Name", "cds_dt", name);
            element(
                    xml,
                    "cdsd",
                    "DataType",
                    "cds_dt",
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract
                            .STRING_TYPE);
            element(xml, "cdsd", "Content", "cds_dt", CdsExportResidualJson.token(json));
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndElement();
            xml.flush();
            xml.close();
        } catch (XMLStreamException failure) {
            throw new IOException("CDS_EXPORT_XML_WRITE_FAILED");
        }
        return output.toByteArray();
    }

    private static void element(
            javax.xml.stream.XMLStreamWriter xml,
            String prefix,
            String local,
            String namespace,
            String text)
            throws XMLStreamException {
        xml.writeStartElement(prefix, local, namespace);
        xml.writeCharacters(text);
        xml.writeEndElement();
    }

    /** Every captured field of the file's link and privilege rows, by table and record key. */
    static Map<String, Map<String, SortedSet<String>>> fields(
            Collection<List<CdsExportCapturedTextRowDto>> rows) {
        var result = new TreeMap<String, Map<String, SortedSet<String>>>();
        for (var list : rows)
            for (var row : list)
                result.computeIfAbsent(row.table(), ignored -> new TreeMap<>())
                        .computeIfAbsent(row.recordKey(), ignored -> new TreeSet<>())
                        .addAll(row.fields().keySet());
        return result;
    }

    private static long number(String text) throws IOException {
        if (text == null || !text.matches("-?[0-9]{1,19}")) invalid();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException invalid) {
            throw new IOException("CDS_EXPORT_MERGE_SCOPE_INVALID");
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_MERGE_SCOPE_INVALID");
    }

}
