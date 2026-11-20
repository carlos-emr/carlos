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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportHeldPatientDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMergeJobSummaryDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMergeSummaryDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The encrypted ReadMe's and event log's wording for charts linked by Merge Records, shared by
 * the supporting-file composer and its independent validator so both say exactly the same. A
 * job, held patient or patient file without Merge Records links has none of these lines, so
 * such a package reads exactly as before.
 */
public final class CdsExportMergedChartReadMe {
    public static final String CHARTS_SELECTED = "Charts selected";
    public static final String PATIENT_FILES =
            "Patient files (charts linked by Merge Records are one file)";
    public static final String SELECTED_ELSEWHERE = "Selected chart in another patient file";
    public static final String RECORDS = "Merged chart records";
    public static final String WRITTEN = "written";
    public static final String NOT_WRITTEN =
            "not exported ("
                    + CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE
                    + ")";
    public static final String MERGED_CHART = "Merged chart";
    public static final String LINKS = "Merge links";
    public static final String SHAPE = "Merge links shape";
    public static final String CHANGE = "Merge links change";
    public static final String HELD_REFUSED = "Held patient refused chart";
    public static final String HELD_SELECTED_AS = "Held patient selected as";
    /** First column of an event-log line of a merged chart. */
    public static final String EVENT = "MERGED_CHART";

    private CdsExportMergedChartReadMe() {}

    /** One ReadMe field. */
    public record Line(String label, String value) {}

    /** The job's lines; none when no chart of the job has a Merge Records link. */
    public static List<Line> job(CdsExportMergeJobSummaryDto merges, Set<CdsCategory> categories) {
        var lines = new ArrayList<Line>();
        if (merges == null) return lines;
        lines.add(new Line(CHARTS_SELECTED, Long.toString(merges.chartsSelected())));
        lines.add(new Line(PATIENT_FILES, Long.toString(merges.patientFiles())));
        for (var chart : merges.elsewhere())
            lines.add(new Line(SELECTED_ELSEWHERE, chart.chart() + " -> " + chart.head()));
        lines.add(
                new Line(
                        RECORDS,
                        categories.contains(CdsCategory.NEW_CATEGORY) ? WRITTEN : NOT_WRITTEN));
        return lines;
    }

    /** A held patient's further lines; none for a chart held alone. */
    public static List<Line> held(CdsExportHeldPatientDto held) {
        var lines = new ArrayList<Line>();
        if (held.refused() > 0) lines.add(new Line(HELD_REFUSED, Integer.toString(held.refused())));
        for (int chart : held.selectedAs())
            lines.add(new Line(HELD_SELECTED_AS, Integer.toString(chart)));
        return lines;
    }

    /** A patient file's lines; none for a file without Merge Records links. */
    public static List<Line> file(CdsExportMergeSummaryDto merge) {
        var lines = new ArrayList<Line>();
        if (merge == null) return lines;
        for (var member : merge.members()) {
            var parts = new ArrayList<String>();
            for (var section : CdsCategory.values()) {
                Long count = member.records().get(section);
                if (count != null && count > 0) parts.add(section.wireName() + " " + count);
            }
            lines.add(
                    new Line(
                            MERGED_CHART,
                            member.chart()
                                    + ": "
                                    + (parts.isEmpty()
                                            ? "no records besides its demographics"
                                            : String.join(", ", parts))));
        }
        lines.add(
                new Line(
                        LINKS,
                        merge.links()
                                + " (active "
                                + merge.activeLinks()
                                + ", deleted "
                                + merge.deletedLinks()
                                + ", outside this file "
                                + merge.outsideLinks()
                                + ")"));
        if (!merge.shape().isEmpty()) lines.add(new Line(SHAPE, String.join(", ", merge.shape())));
        for (String change : merge.changes()) lines.add(new Line(CHANGE, change));
        return lines;
    }

    /**
     * The event-log lines of a file's merged charts: the chart and the head of its file, then,
     * per section, how many of its records the file holds ({@code NOT_SELECTED} outside the
     * job's scope; its Demographics is in its record, so 0), then its Reports representations.
     */
    public static List<List<String>> events(
            int head, CdsExportMergeSummaryDto merge, Set<CdsCategory> sections) {
        var lines = new ArrayList<List<String>>();
        if (merge == null) return lines;
        for (var member : merge.members()) {
            var line = new ArrayList<String>();
            line.add(EVENT + " " + member.chart() + " -> " + head);
            for (var section : CdsCategory.values())
                line.add(
                        !sections.contains(section)
                                ? "NOT_SELECTED"
                                : Long.toString(member.records().getOrDefault(section, 0L)));
            line.add(
                    member.textReports() == null
                            ? "NOT_SELECTED"
                            : Long.toString(member.textReports()));
            line.add(
                    member.binaryReports() == null
                            ? "NOT_SELECTED"
                            : Long.toString(member.binaryReports()));
            lines.add(line);
        }
        return lines;
    }
}
