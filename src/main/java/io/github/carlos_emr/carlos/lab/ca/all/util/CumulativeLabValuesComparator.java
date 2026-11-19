/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/*
 * CumulativeLabValuesComparator.java
 *
 * Created on August 3, 2007, 3:57 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * A custom comparator used to compare the Hashtables within an array by the
 * date value.
 * <p>
 * Used by /oscar/lab/ViewCumulativeLabValues3
 *
 * @author wrighd
 */
public class CumulativeLabValuesComparator implements Comparator {

    /** Newest column first; a column whose time is unknown or unreadable sorts last. */
    public int compare(Object o1, Object o2) {
        Date dateA = columnDate((String) ((Hashtable) o1).get("date"));
        Date dateB = columnDate((String) ((Hashtable) o2).get("date"));
        if (dateA == null || dateB == null) {
            return dateA == dateB ? 0 : dateA == null ? 1 : -1;
        }
        return dateB.compareTo(dateA);
    }

    private static Date columnDate(String date) {
        if (date == null) return null;
        Date full = UtilDateUtilities.getDateFromString(date, "yyyy-MM-dd HH:mm:ss");
        // Source-only columns can carry a handler time without seconds or without a time of day.
        return full != null ? full : io.github.carlos_emr.carlos.lab.ca.all.util.ObservationTime.parse(date);
    }

}
