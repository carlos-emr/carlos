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

import java.util.List;

/**
 * What the encrypted ReadMe says about Merge Records for the whole job: how many charts the job
 * selected, how many patient files they made, and each selected chart that is in another chart's
 * patient file. Present only when some chart of the job has a Merge Records link or privilege row.
 *
 * @param elsewhere each selected chart in another patient file, with the head of that file
 * @since 2026-11-14
 */
public record CdsExportMergeJobSummaryDto(
        long chartsSelected, long patientFiles, List<Elsewhere> elsewhere) {
    public CdsExportMergeJobSummaryDto {
        elsewhere = List.copyOf(elsewhere);
        if (chartsSelected < 1 || patientFiles < 1)
            throw new IllegalArgumentException("CDS_EXPORT_MERGE_SUMMARY_INVALID");
    }

    /** A selected chart whose records are in the patient file of {@code head}. */
    public record Elsewhere(int chart, int head) {
        public Elsewhere {
            if (chart < 1 || head < 1 || chart == head)
                throw new IllegalArgumentException("CDS_EXPORT_MERGE_SUMMARY_INVALID");
        }
    }

    @Override
    public String toString() {
        return "CdsExportMergeJobSummaryDto[protected]";
    }
}
