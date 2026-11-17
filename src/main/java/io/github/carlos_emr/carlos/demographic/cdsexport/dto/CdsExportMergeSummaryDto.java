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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.List;
import java.util.Map;

/**
 * What the encrypted ReadMe says about one patient file of charts linked by Merge Records, or of
 * a chart with Merge Records links or privilege rows: each merged chart and how many of its
 * records each section holds, the file's links (active, deleted, naming a chart outside the
 * file), the group's shape and change notes, and whether the NewCategory records were written.
 *
 * @param members the merged charts, ascending (none for a chart alone)
 * @param written whether the CARLOS.MergedChart.v1 and CARLOS.MergeLinks.v1 records are in the
 *     file (NewCategory selected)
 * @since 2026-11-14
 */
public record CdsExportMergeSummaryDto(
        List<Member> members,
        long activeLinks,
        long deletedLinks,
        long outsideLinks,
        List<String> shape,
        List<String> changes,
        boolean written) {
    public CdsExportMergeSummaryDto {
        members = List.copyOf(members);
        shape = List.copyOf(shape);
        changes = List.copyOf(changes);
        if (activeLinks < 0 || deletedLinks < 0 || outsideLinks < 0)
            throw new IllegalArgumentException("CDS_EXPORT_MERGE_SUMMARY_INVALID");
    }

    /**
     * One merged chart of the file.
     *
     * @param records per section, how many of its records the file holds (its Demographics is in
     *     its CARLOS.MergedChart.v1 record)
     * @param textReports its Reports with text content, or null when Reports is not in the file
     * @param binaryReports its Reports with a file, or null when Reports is not in the file
     */
    public record Member(
            int chart, Map<CdsCategory, Long> records, Long textReports, Long binaryReports) {
        public Member {
            records = Map.copyOf(records);
            if (chart < 1
                    || records.containsKey(CdsCategory.DEMOGRAPHICS)
                    || records.values().stream().anyMatch(count -> count == null || count < 1)
                    || (textReports == null) != (binaryReports == null)
                    || textReports != null && (textReports < 0 || binaryReports < 0))
                throw new IllegalArgumentException("CDS_EXPORT_MERGE_SUMMARY_INVALID");
        }
    }

    /** The total number of links. */
    public long links() {
        return activeLinks + deletedLinks;
    }

    @Override
    public String toString() {
        return "CdsExportMergeSummaryDto[protected]";
    }
}
