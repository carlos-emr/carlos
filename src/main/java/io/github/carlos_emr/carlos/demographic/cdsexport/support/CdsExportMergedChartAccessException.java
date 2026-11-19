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
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

/**
 * An access check refused one chart of a group linked by Merge Records. The group is one patient
 * file holding every chart's records, so it is held whole
 * ({@link CdsExportReviewException.Reason#MERGED_CHART_ACCESS}). The refused chart is kept for the
 * protected held-patient record and the encrypted ReadMe; it is never part of the message, which
 * is only the safe code, and is never logged.
 *
 * @since 2026-11-14
 */
public final class CdsExportMergedChartAccessException extends SecurityException {
    private static final long serialVersionUID = 1L;
    private final int chart;

    /**
     * @param chart the refused chart
     * @param cause the refusal of that chart's check
     */
    public CdsExportMergedChartAccessException(int chart, Throwable cause) {
        super(CdsExportReviewException.Reason.MERGED_CHART_ACCESS.code(), cause);
        if (chart < 1) throw new IllegalArgumentException("CDS_EXPORT_MERGED_CHART_INVALID");
        this.chart = chart;
    }

    /** The refused chart. */
    public int chart() {
        return chart;
    }
}
