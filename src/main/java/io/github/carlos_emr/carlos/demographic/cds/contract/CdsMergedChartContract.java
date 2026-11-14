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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.ValidatorHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The rules the CARLOS exporter and importer share for charts linked by Merge Records
 * ({@code demographic_merged}).
 *
 * <p><b>Groups.</b> A link row is <em>active</em> when its {@code deleted} is 0 and both charts it
 * names exist; any other {@code deleted} value is history. A chart's <em>parent</em> is the chart
 * its active outgoing row with the greatest {@code id} names (the row CARLOS's own head lookup
 * ends up following); its <em>head</em> is reached by following parents to a chart that has none.
 * A walk that comes back to a chart it has passed has found a cycle, from older data (the Merge
 * Records page refuses new ones): the head is then the smallest chart number in the cycle. A
 * chart's <em>group</em> is every chart with the same head. The shape of a group is recorded,
 * never held: {@link Shape}.
 *
 * <p><b>Records.</b> A patient file of a group holds every record of every chart of the group, the
 * head's first in each section. Its NewCategory section ends with one {@value #CHART_RECORD}
 * record per merged chart ({@link ChartRecord}), then one {@value #LINKS_RECORD} record ({@link
 * LinksRecord}) whenever any chart of the file has a link or privilege row. A chart is named by its
 * sender identifier, {@code C} followed by its chart number ({@link #senderId}).
 */
public final class CdsMergedChartContract {
    /** CategoryName and residual DataElement name of the record of one merged chart. */
    public static final String CHART_RECORD = CdsNativeExtensionContract.MERGED_CHART;

    /** CategoryDescription of that record. */
    public static final String CHART_DESCRIPTION =
            "Chart merged into this patient's chart in CARLOS (Merge Records)";

    /** CategoryName and residual DataElement name of the record of the file's link rows. */
    public static final String LINKS_RECORD = CdsNativeExtensionContract.MERGE_LINKS;

    /** CategoryDescription of that record. */
    public static final String LINKS_DESCRIPTION =
            "Merge Records links and chart privileges of the charts in this patient file";

    /** The JSON version both records carry. */
    public static final int VERSION = 1;

    /** Field rule of every carried link and privilege column. */
    public static final String LINK_RULE = "EXACT_MERGE_LINK";

    /** Change note: a chart of the file was merged or unmerged after the job selected it. */
    public static final String CHANGED_AFTER_SELECTION = "MERGE_CHANGED_AFTER_SELECTION";

    /** The privilege row Merge Records writes for a merged chart: role. */
    public static final String PRIVILEGE_ROLE = "_all";

    /** The privilege row Merge Records writes for a merged chart: object, before the number. */
    public static final String PRIVILEGE_OBJECT_PREFIX = "_eChart$";

    /** A group's shape, recorded with its links; none holds a chart. */
    public enum Shape {
        /** A chart of the group is merged into a chart that is itself merged. */
        CHAIN,
        /** A chart has two or more active outgoing links; only the newest one is followed. */
        FORK,
        /** Active links go round; the smallest chart number of the cycle is the head. */
        CYCLE,
        /** An active link names the same chart at both ends. */
        SELF_LINK,
        /** An active link names a chart that does not exist; it is carried and not followed. */
        DANGLING
    }

    /** The JSON the records are written and read with. */
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            // A record holds one standalone demographic file of
                                            // any length: no string length refuses it.
                                            StreamReadConstraints.builder()
                                                    .maxStringLength(Integer.MAX_VALUE)
                                                    .maxNestingDepth(16)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .build();

    private CdsMergedChartContract() {}

    // ------------------------------------------------------------------------------------------
    // Sender identifiers
    // ------------------------------------------------------------------------------------------

    /** The sender identifier of a chart: {@code C} followed by its number. */
    public static String senderId(int chart) {
        if (chart < 1) throw invalid();
        return "C" + chart;
    }

    /**
     * The chart a sender identifier names.
     *
     * @throws IllegalArgumentException {@code CDS_MERGED_CHART_CARRIER_INVALID} unless it is
     *     {@code C} followed by a positive chart number without leading zeros
     */
    public static int chart(String senderId) {
        if (senderId == null || !senderId.matches("C[1-9][0-9]{0,9}")) throw invalid();
        long value = Long.parseLong(senderId.substring(1));
        if (value > Integer.MAX_VALUE) throw invalid();
        return (int) value;
    }

    // ------------------------------------------------------------------------------------------
    // The group rule
    // ------------------------------------------------------------------------------------------

    /**
     * One {@code demographic_merged} row as the group rule reads it.
     *
     * @param id the row's identifier
     * @param chart {@code demographic_no}, the chart merged away
     * @param mergedTo {@code merged_to}, the chart it was merged into
     * @param deleted the stored {@code deleted} value; only 0 is active
     */
    public record Link(long id, int chart, int mergedTo, int deleted) {}

    /**
     * Applies the group rule to a set of rows.
     *
     * @param rows every row touching the charts to group (rows of any {@code deleted} value)
     * @param exists whether a chart number names an existing chart
     */
    public static Groups groups(Collection<Link> rows, IntPredicate exists) {
        Objects.requireNonNull(exists);
        var charts = new TreeSet<Integer>();
        var outgoing = new HashMap<Integer, List<Link>>();
        var flags = new HashMap<Integer, EnumSet<Shape>>();
        var seen = new java.util.HashSet<Long>();
        for (var row : List.copyOf(rows)) {
            if (row == null || !seen.add(row.id())) throw invalid();
            boolean from = row.chart() > 0 && exists.test(row.chart());
            boolean to = row.mergedTo() > 0 && exists.test(row.mergedTo());
            if (from) charts.add(row.chart());
            if (to) charts.add(row.mergedTo());
            if (row.deleted() != 0) continue;
            if (!from || !to) {
                // A link to or from a chart that does not exist is carried, never followed.
                if (from) flag(flags, row.chart(), Shape.DANGLING);
                if (to) flag(flags, row.mergedTo(), Shape.DANGLING);
                continue;
            }
            outgoing.computeIfAbsent(row.chart(), ignored -> new ArrayList<>()).add(row);
            if (row.chart() == row.mergedTo()) flag(flags, row.chart(), Shape.SELF_LINK);
        }
        var parents = new HashMap<Integer, Integer>();
        outgoing.forEach(
                (chart, links) -> {
                    Link newest = Collections.max(links, java.util.Comparator.comparingLong(Link::id));
                    parents.put(chart, newest.mergedTo());
                    if (links.size() > 1) flag(flags, chart, Shape.FORK);
                });
        var heads = new HashMap<Integer, Integer>();
        var cycles = new java.util.HashSet<Integer>();
        for (int chart : charts) head(chart, parents, heads, cycles);
        for (int chart : cycles) if (parents.get(chart) != chart) flag(flags, chart, Shape.CYCLE);
        return new Groups(charts, parents, heads, flags);
    }

    private static void flag(Map<Integer, EnumSet<Shape>> flags, int chart, Shape shape) {
        flags.computeIfAbsent(chart, ignored -> EnumSet.noneOf(Shape.class)).add(shape);
    }

    /** Follows parents from one chart; every chart on the walk learns its head. */
    private static int head(
            int chart,
            Map<Integer, Integer> parents,
            Map<Integer, Integer> heads,
            Set<Integer> cycles) {
        var path = new LinkedHashSet<Integer>();
        int current = chart;
        int head;
        while (true) {
            Integer known = heads.get(current);
            if (known != null) {
                head = known;
                break;
            }
            if (!path.add(current)) {
                // The walk came back: the cycle is the part of the path from here on.
                var cycle = new ArrayList<Integer>();
                boolean inside = false;
                for (int step : path) {
                    if (step == current) inside = true;
                    if (inside) cycle.add(step);
                }
                cycles.addAll(cycle);
                head = Collections.min(cycle);
                break;
            }
            Integer parent = parents.get(current);
            if (parent == null) {
                head = current;
                break;
            }
            current = parent;
        }
        for (int step : path) heads.put(step, head);
        return head;
    }

    /** The groups of a set of rows. */
    public static final class Groups {
        private final SortedSet<Integer> charts;
        private final Map<Integer, Integer> parents;
        private final Map<Integer, Integer> heads;
        private final Map<Integer, EnumSet<Shape>> flags;

        private Groups(
                SortedSet<Integer> charts,
                Map<Integer, Integer> parents,
                Map<Integer, Integer> heads,
                Map<Integer, EnumSet<Shape>> flags) {
            this.charts = Collections.unmodifiableSortedSet(new TreeSet<>(charts));
            this.parents = Map.copyOf(parents);
            this.heads = Map.copyOf(heads);
            this.flags = new HashMap<>();
            flags.forEach((chart, shapes) -> this.flags.put(chart, EnumSet.copyOf(shapes)));
        }

        /** The existing charts the rows name, ascending. */
        public SortedSet<Integer> charts() {
            return charts;
        }

        /** The head of a chart: itself when no active link leaves it. */
        public int head(int chart) {
            return heads.getOrDefault(chart, chart);
        }

        /** The chart a chart's followed link names, or 0 when it has none. */
        public int parent(int chart) {
            return parents.getOrDefault(chart, 0);
        }

        /** Every chart whose head is {@code head}, ascending; the head itself included. */
        public SortedSet<Integer> group(int head) {
            var group = new TreeSet<Integer>();
            group.add(head);
            for (int chart : charts) if (head(chart) == head) group.add(chart);
            return Collections.unmodifiableSortedSet(group);
        }

        /** The shape of the group whose head is {@code head}. */
        public SortedSet<Shape> shape(int head) {
            var shape = EnumSet.noneOf(Shape.class);
            for (int chart : group(head)) {
                shape.addAll(flags.getOrDefault(chart, EnumSet.noneOf(Shape.class)));
                int parent = parent(chart);
                if (chart != head && parent != 0 && parent != head && parent != chart)
                    shape.add(Shape.CHAIN);
            }
            return Collections.unmodifiableSortedSet(new TreeSet<>(shape));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------------------------------

    /**
     * Where a merged chart's records of one section are in the patient file.
     *
     * @param first 1-based position of the chart's first record among the section's elements
     * @param count how many consecutive records are the chart's
     */
    public record Range(long first, long count) {
        public Range {
            if (first < 1 || count < 1 || first > Long.MAX_VALUE - count) throw invalid();
        }

        /** 1-based position of the chart's last record. */
        public long last() {
            return first + count - 1;
        }
    }

    /**
     * Checks the merged charts' ranges against the patient file: every section named is a
     * patient-record section other than Demographics, every range lies inside its section, and no
     * two ranges of one section overlap; every other position is the head's.
     *
     * @param sections the number of elements of each section of the patient file
     * @param charts the ranges of each merged chart
     */
    public static void requirePositions(
            Map<CdsCategory, Long> sections, Collection<SortedMap<CdsCategory, Range>> charts) {
        var used = new EnumMap<CdsCategory, TreeMap<Long, Long>>(CdsCategory.class);
        for (var positions : charts)
            for (var entry : positions.entrySet()) {
                var section = entry.getKey();
                var range = entry.getValue();
                long total = sections.getOrDefault(section, 0L);
                if (section == CdsCategory.DEMOGRAPHICS || range == null || range.last() > total)
                    throw invalid();
                var taken = used.computeIfAbsent(section, ignored -> new TreeMap<>());
                var before = taken.floorEntry(range.last());
                if (before != null && before.getValue() >= range.first()) throw invalid();
                taken.put(range.first(), range.last());
            }
    }

    // ------------------------------------------------------------------------------------------
    // The record of one merged chart
    // ------------------------------------------------------------------------------------------

    /**
     * The {@value #CHART_RECORD} record of one merged chart.
     *
     * @param chart the merged chart
     * @param head the chart of the patient file
     * @param positions per section, where the chart's records are in the file (NewCategory
     *     included; Demographics never, it is in the document)
     * @param document the chart's own standalone CDS file, holding only its Demographics and, when
     *     written, its {@code CARLOS.NativeDemographic.v1} records
     * @param documentSha256 SHA-256 of the document's UTF-8 bytes
     */
    public record ChartRecord(
            int chart,
            int head,
            SortedMap<CdsCategory, Range> positions,
            String document,
            String documentSha256) {
        public ChartRecord {
            if (chart < 1
                    || head < 1
                    || chart == head
                    || positions == null
                    || document == null
                    || documentSha256 == null
                    || !documentSha256.matches("[0-9a-f]{64}")) throw invalid();
            positions = Collections.unmodifiableSortedMap(new TreeMap<>(positions));
            if (positions.containsKey(CdsCategory.DEMOGRAPHICS)) throw invalid();
        }

        @Override
        public String toString() {
            return "CdsMergedChartContract.ChartRecord[protected]";
        }
    }

    /** The record's compact JSON, keys in a fixed order (the caller escapes it for xs:token). */
    public static String json(ChartRecord record) {
        ObjectNode node = JSON.createObjectNode();
        node.put("version", VERSION);
        node.put("chart", senderId(record.chart()));
        node.put("head", senderId(record.head()));
        ObjectNode positions = node.putObject("positions");
        for (var entry : record.positions().entrySet())
            positions
                    .putObject(entry.getKey().wireName())
                    .put("first", entry.getValue().first())
                    .put("count", entry.getValue().count());
        node.put("document", record.document());
        node.put("documentSha256", record.documentSha256());
        return write(node);
    }

    /**
     * Reads one {@value #CHART_RECORD} record strictly: exactly these members, these types, and a
     * document whose SHA-256 is the one it states.
     *
     * @throws IllegalArgumentException {@code CDS_MERGED_CHART_CARRIER_INVALID} otherwise
     */
    public static ChartRecord chartRecord(String json) {
        JsonNode node = read(json);
        if (!(node instanceof ObjectNode object)
                || !names(object).equals(
                        List.of(
                                "version",
                                "chart",
                                "head",
                                "positions",
                                "document",
                                "documentSha256"))
                || !object.path("version").isInt()
                || object.path("version").intValue() != VERSION
                || !object.path("chart").isTextual()
                || !object.path("head").isTextual()
                || !(object.path("positions") instanceof ObjectNode positions)
                || !object.path("document").isTextual()
                || !object.path("documentSha256").isTextual()) throw invalid();
        var ranges = new TreeMap<CdsCategory, Range>();
        for (var iterator = positions.fields(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (!CdsCategory.wireNames().contains(entry.getKey())
                    || !(entry.getValue() instanceof ObjectNode range)
                    || !names(range).equals(List.of("first", "count"))
                    || !range.path("first").canConvertToLong()
                    || !range.path("first").isIntegralNumber()
                    || !range.path("count").canConvertToLong()
                    || !range.path("count").isIntegralNumber()) throw invalid();
            var section = CdsCategory.fromWireName(entry.getKey());
            ranges.put(
                    section,
                    new Range(range.path("first").longValue(), range.path("count").longValue()));
        }
        var record =
                new ChartRecord(
                        chart(object.path("chart").textValue()),
                        chart(object.path("head").textValue()),
                        ranges,
                        object.path("document").textValue(),
                        object.path("documentSha256").textValue());
        if (!sha256(record.document().getBytes(StandardCharsets.UTF_8))
                .equals(record.documentSha256())) throw invalid();
        return record;
    }

    // ------------------------------------------------------------------------------------------
    // The record of the file's links
    // ------------------------------------------------------------------------------------------

    /**
     * One {@code demographic_merged} row, every column as stored (SQL NULL as null).
     *
     * @param outside whether a chart it names is not in the patient file
     */
    public record LinkRow(
            String id,
            String demographicNo,
            String mergedTo,
            String deleted,
            String lastUpdateUser,
            String lastUpdateDate,
            boolean outside) {
        public LinkRow {
            if (id == null
                    || !id.matches("-?[0-9]{1,19}")
                    || demographicNo == null
                    || !demographicNo.matches("-?[0-9]{1,10}")
                    || mergedTo == null
                    || !mergedTo.matches("-?[0-9]{1,10}")
                    || deleted == null
                    || !deleted.matches("-?[0-9]{1,10}")) throw invalid();
        }

        @Override
        public String toString() {
            return "CdsMergedChartContract.LinkRow[protected]";
        }
    }

    /**
     * The chart privilege row Merge Records writes for a merged chart ({@code secObjPrivilege},
     * role {@value #PRIVILEGE_ROLE}, object {@value #PRIVILEGE_OBJECT_PREFIX} and the chart
     * number), every column as stored (SQL NULL as null). It grants nothing a chart reader needs
     * and is never an authorization input; it is carried so nothing of the chart is lost.
     */
    public record PrivilegeRow(
            int chart,
            String roleUserGroup,
            String objectName,
            String privilege,
            String priority,
            String providerNo) {
        public PrivilegeRow {
            if (chart < 1 || roleUserGroup == null || objectName == null || privilege == null)
                throw invalid();
        }

        @Override
        public String toString() {
            return "CdsMergedChartContract.PrivilegeRow[protected]";
        }
    }

    /**
     * The {@value #LINKS_RECORD} record of a patient file.
     *
     * @param charts the charts of the file: the head, then the merged charts ascending
     * @param links every link row naming a chart of the file, in {@code id} order
     * @param privileges the privilege rows of the file's charts, by chart
     * @param shape the group's shape
     * @param changes change notes ({@link #CHANGED_AFTER_SELECTION})
     */
    public record LinksRecord(
            List<Integer> charts,
            List<LinkRow> links,
            List<PrivilegeRow> privileges,
            SortedSet<Shape> shape,
            SortedSet<String> changes) {
        public LinksRecord {
            charts = List.copyOf(charts);
            links = List.copyOf(links);
            privileges = List.copyOf(privileges);
            shape = Collections.unmodifiableSortedSet(new TreeSet<>(shape));
            changes = Collections.unmodifiableSortedSet(new TreeSet<>(changes));
            if (charts.isEmpty() || Set.copyOf(charts).size() != charts.size()) throw invalid();
            for (int index = 2; index < charts.size(); index++)
                if (charts.get(index) <= charts.get(index - 1)) throw invalid();
            long previous = Long.MIN_VALUE;
            for (var link : links) {
                long id = Long.parseLong(link.id());
                if (id <= previous) throw invalid();
                previous = id;
            }
            int last = 0;
            for (var privilege : privileges) {
                if (privilege.chart() <= last || !charts.contains(privilege.chart()))
                    throw invalid();
                last = privilege.chart();
            }
            for (String change : changes) if (!change.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid();
        }

        @Override
        public String toString() {
            return "CdsMergedChartContract.LinksRecord[protected]";
        }
    }

    /** The record's compact JSON, keys in a fixed order (the caller escapes it for xs:token). */
    public static String json(LinksRecord record) {
        ObjectNode node = JSON.createObjectNode();
        node.put("version", VERSION);
        ArrayNode charts = node.putArray("charts");
        for (int chart : record.charts()) charts.add(senderId(chart));
        ArrayNode links = node.putArray("links");
        for (var link : record.links()) {
            ObjectNode row = links.addObject();
            row.put("id", link.id());
            row.put("demographic_no", link.demographicNo());
            row.put("merged_to", link.mergedTo());
            row.put("deleted", link.deleted());
            row.put("lastUpdateUser", link.lastUpdateUser());
            row.put("lastUpdateDate", link.lastUpdateDate());
            if (link.outside()) row.put("outside", true);
        }
        ArrayNode privileges = node.putArray("privileges");
        for (var privilege : record.privileges()) {
            ObjectNode row = privileges.addObject();
            row.put("chart", senderId(privilege.chart()));
            row.put("roleUserGroup", privilege.roleUserGroup());
            row.put("objectName", privilege.objectName());
            row.put("privilege", privilege.privilege());
            row.put("priority", privilege.priority());
            row.put("provider_no", privilege.providerNo());
        }
        ArrayNode shape = node.putArray("shape");
        for (var value : record.shape()) shape.add(value.name());
        ArrayNode changes = node.putArray("changes");
        for (String change : record.changes()) changes.add(change);
        return write(node);
    }

    /**
     * Reads one {@value #LINKS_RECORD} record strictly.
     *
     * @throws IllegalArgumentException {@code CDS_MERGED_CHART_CARRIER_INVALID} unless it has
     *     exactly the members {@link #json(LinksRecord)} writes, with valid sender identifiers
     */
    public static LinksRecord linksRecord(String json) {
        JsonNode node = read(json);
        if (!(node instanceof ObjectNode object)
                || !names(object)
                        .equals(
                                List.of(
                                        "version",
                                        "charts",
                                        "links",
                                        "privileges",
                                        "shape",
                                        "changes"))
                || !object.path("version").isInt()
                || object.path("version").intValue() != VERSION
                || !object.path("charts").isArray()
                || !object.path("links").isArray()
                || !object.path("privileges").isArray()
                || !object.path("shape").isArray()
                || !object.path("changes").isArray()) throw invalid();
        var charts = new ArrayList<Integer>();
        for (var chart : object.path("charts")) {
            if (!chart.isTextual()) throw invalid();
            charts.add(chart(chart.textValue()));
        }
        var links = new ArrayList<LinkRow>();
        for (var link : object.path("links")) {
            if (!(link instanceof ObjectNode row)) throw invalid();
            var expected =
                    new ArrayList<>(
                            List.of(
                                    "id",
                                    "demographic_no",
                                    "merged_to",
                                    "deleted",
                                    "lastUpdateUser",
                                    "lastUpdateDate"));
            if (row.has("outside")) expected.add("outside");
            if (!names(row).equals(expected)
                    || row.has("outside") && !row.path("outside").asBoolean(false)
                    || row.has("outside") && !row.path("outside").isBoolean()) throw invalid();
            links.add(
                    new LinkRow(
                            text(row, "id", false),
                            text(row, "demographic_no", false),
                            text(row, "merged_to", false),
                            text(row, "deleted", false),
                            text(row, "lastUpdateUser", true),
                            text(row, "lastUpdateDate", true),
                            row.has("outside")));
        }
        var privileges = new ArrayList<PrivilegeRow>();
        for (var privilege : object.path("privileges")) {
            if (!(privilege instanceof ObjectNode row)
                    || !names(row)
                            .equals(
                                    List.of(
                                            "chart",
                                            "roleUserGroup",
                                            "objectName",
                                            "privilege",
                                            "priority",
                                            "provider_no"))
                    || !row.path("chart").isTextual()) throw invalid();
            privileges.add(
                    new PrivilegeRow(
                            chart(row.path("chart").textValue()),
                            text(row, "roleUserGroup", false),
                            text(row, "objectName", false),
                            text(row, "privilege", false),
                            text(row, "priority", true),
                            text(row, "provider_no", true)));
        }
        var shape = new TreeSet<Shape>();
        for (var value : object.path("shape")) {
            if (!value.isTextual()) throw invalid();
            try {
                if (!shape.add(Shape.valueOf(value.textValue()))) throw invalid();
            } catch (IllegalArgumentException unknown) {
                throw invalid();
            }
        }
        var changes = new TreeSet<String>();
        for (var value : object.path("changes")) {
            if (!value.isTextual() || !changes.add(value.textValue())) throw invalid();
        }
        return new LinksRecord(charts, links, privileges, shape, changes);
    }

    private static String text(ObjectNode row, String name, boolean nullable) {
        JsonNode value = row.get(name);
        if (value == null || value.isNull() && !nullable || !value.isNull() && !value.isTextual())
            throw invalid();
        return value.isNull() ? null : value.textValue();
    }

    // ------------------------------------------------------------------------------------------
    // The standalone document of a merged chart
    // ------------------------------------------------------------------------------------------

    /**
     * Checks a merged chart's standalone document: its SHA-256 is the one the record states, it is
     * a schema-valid CDS file of one patient whose sender identifier is the chart's, and it holds
     * the chart's Demographics and nothing but {@code CARLOS.NativeDemographic.v1} records beside
     * it.
     *
     * @throws IllegalArgumentException {@code CDS_MERGED_CHART_CARRIER_INVALID} otherwise
     */
    // FindSecBugs XXE_XMLREADER: The reader comes from CdsXmlReaders.reader, which sets secure processing and disallow-doctype-decl, turns off external general and parameter entities and external DTD loading, clears ACCESS_EXTERNAL_DTD and installs a resolver that always refuses.
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "XXE_XMLREADER",
            justification =
                    "the reader comes from CdsXmlReaders.reader, which sets secure processing and"
                        + " disallow-doctype-decl, turns off external general and parameter"
                        + " entities and external DTD loading, clears ACCESS_EXTERNAL_DTD and"
                        + " installs a resolver that always refuses")
    public static void verifyDocument(ChartRecord record) {
        byte[] bytes = record.document().getBytes(StandardCharsets.UTF_8);
        if (!sha256(bytes).equals(record.documentSha256())) throw invalid();
        var shape = new DocumentShape();
        try {
            XMLReader reader = CdsXmlReaders.reader(256, 1024, 256, 128, 64);
            ValidatorHandler schema = CdsWireSchema.newValidator();
            schema.setContentHandler(shape);
            schema.setErrorHandler(shape);
            reader.setContentHandler(schema);
            reader.setErrorHandler(shape);
            var decoder =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
            reader.parse(
                    new InputSource(
                            CdsXmlReaders.markupReader(
                                    new InputStreamReader(new ByteArrayInputStream(bytes), decoder),
                                    65_536,
                                    IOException::new)));
        } catch (SAXException | IOException | ParserConfigurationException failure) {
            throw invalid();
        }
        if (shape.patients != 1
                || shape.demographics != 1
                || !senderId(record.chart()).equals(shape.vendorId)
                || shape.other) throw invalid();
    }

    /** What the document holds, read as it is validated. */
    private static final class DocumentShape extends DefaultHandler {
        /** Elements open from the root: OmdCds, PatientRecord, the section, its children. */
        private int depth;
        private final StringBuilder text = new StringBuilder();
        private int patients, demographics;
        private boolean other;
        private String vendorId;
        private String section;
        private boolean capturing;

        @Override
        public void startElement(String uri, String local, String qualified, Attributes attributes) {
            if (depth == 1 && "PatientRecord".equals(local)) patients++;
            if (depth == 2) {
                section = local;
                if ("Demographics".equals(local)) demographics++;
                else if (!"NewCategory".equals(local)) other = true;
            }
            if (depth == 3
                    && ("Demographics".equals(section) && "UniqueVendorIdSequence".equals(local)
                            || "NewCategory".equals(section) && "CategoryName".equals(local))) {
                capturing = true;
                text.setLength(0);
            }
            depth++;
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            if (capturing) {
                if (text.length() + length > 4096) other = true;
                else text.append(characters, start, length);
            }
        }

        @Override
        public void endElement(String uri, String local, String qualified) {
            depth--;
            if (!capturing || depth != 3) return;
            capturing = false;
            if ("UniqueVendorIdSequence".equals(local)) {
                if (vendorId != null) other = true;
                vendorId = text.toString();
            } else if ("CategoryName".equals(local)
                    && !CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY.equals(
                            text.toString())) other = true;
        }

        @Override
        public void error(SAXParseException failure) throws SAXException {
            throw failure;
        }

        @Override
        public void fatalError(SAXParseException failure) throws SAXException {
            throw failure;
        }
    }

    // ------------------------------------------------------------------------------------------

    /** SHA-256 of bytes, as lowercase hexadecimal. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<String> names(ObjectNode node) {
        var names = new ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode read(String json) {
        if (json == null) throw invalid();
        try {
            return JSON.readTree(json);
        } catch (IOException malformed) {
            throw invalid();
        }
    }

    private static String write(ObjectNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (IOException impossible) {
            throw new IllegalStateException("CDS_MERGED_CHART_RECORD_ENCODING_FAILED");
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("CDS_MERGED_CHART_CARRIER_INVALID");
    }
}
