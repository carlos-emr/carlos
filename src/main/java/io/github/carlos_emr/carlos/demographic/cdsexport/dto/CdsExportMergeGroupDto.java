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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import java.util.List;

/**
 * One group of the job's frozen group plan ({@code COHORT_GROUPS}): charts that Merge Records
 * links and the selection reached, exported as one patient file
 * ({@link CdsMergedChartContract}). A selected chart with no active link is not in the plan and
 * is a patient file of its own.
 *
 * @param head the chart of the patient file
 * @param physicianNo the head's primary physician when the selection was read (its folder)
 * @param members the other charts of the group, ascending
 * @param selected the charts of the group the job selected, ascending
 * @param links the active link rows touching a chart of the group when it was selected,
 *     ascending
 * @param shape the group's shape ({@link CdsMergedChartContract.Shape} names), ascending
 * @since 2026-11-14
 */
public record CdsExportMergeGroupDto(
        int head,
        String physicianNo,
        List<Integer> members,
        List<Integer> selected,
        List<Integer> links,
        List<String> shape) {
    public CdsExportMergeGroupDto {
        members = List.copyOf(members);
        selected = List.copyOf(selected);
        links = List.copyOf(links);
        shape = List.copyOf(shape);
        if (head < 1
                || !io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto.validPhysician(physicianNo)
                || !ascending(members, 1)
                || members.contains(head)
                || selected.isEmpty()
                || !ascending(selected, 1)
                || !ascending(links, 1)) invalid();
        for (int chart : selected) if (chart != head && !members.contains(chart)) invalid();
        String previous = null;
        for (String value : shape) {
            try {
                CdsMergedChartContract.Shape.valueOf(value);
            } catch (IllegalArgumentException unknown) {
                invalid();
            }
            if (previous != null && previous.compareTo(value) >= 0) invalid();
            previous = value;
        }
    }

    /** Every chart of the group: the head, then the members ascending. */
    public List<Integer> charts() {
        var charts = new java.util.ArrayList<Integer>(1 + members.size());
        charts.add(head);
        charts.addAll(members);
        return List.copyOf(charts);
    }

    /** Whether the chart is one of the group's. */
    public boolean contains(int chart) {
        return chart == head || java.util.Collections.binarySearch(members, chart) >= 0;
    }

    private static boolean ascending(List<Integer> values, int floor) {
        int previous = floor - 1;
        for (Integer value : values) {
            if (value == null || value < floor || value <= previous) return false;
            previous = value;
        }
        return true;
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_EXPORT_COHORT_INVENTORY_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportMergeGroupDto[protected]";
    }
}
