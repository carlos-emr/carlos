/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.model;

import java.text.NumberFormat;

/**
 * A count out of a population size, shown as a percentage on the CAISI Population Report.
 *
 * <p>A population size of 0 is a real state: a site with no active patient admitted to an
 * active Service program. The share is then 0%, rather than the division by zero that made the
 * report answer HTTP 500 (issue #4855).</p>
 */
public class ReportStatistic implements Comparable<ReportStatistic> {

    private String label;
    private int count;
    private int size;

    public ReportStatistic(int count, int size) {
        this.count = count;
        this.size = size;
    }

    public String getLabel() {
        return label;
    }

    public int getCount() {
        return count;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getPercent() {
        NumberFormat percentInstance = NumberFormat.getPercentInstance();
        percentInstance.setMinimumFractionDigits(0);
        percentInstance.setMaximumFractionDigits(2);

        return percentInstance.format(share());
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int compareTo(ReportStatistic rhs) {
        int compareTo = 0;

        if (label != null) {
            compareTo = label.compareTo(rhs.label);
        }

        if (compareTo == 0) {
            compareTo = Float.compare(share(), rhs.share());
        }

        return compareTo;
    }

    /** count / size, or 0 when the size is 0. */
    private float share() {
        return size == 0 ? 0f : (float) count / size;
    }

}
