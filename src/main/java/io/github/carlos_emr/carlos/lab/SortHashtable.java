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

package io.github.carlos_emr.carlos.lab;

import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

/**
 * Comparator for sorting lab result collections (Map or Hashtable instances) by their
 * "collDate" (collection date) field in descending order (newest first).
 * Each object being compared must be a Map or Hashtable containing a "collDate" key
 * with a {@link Date} value.
 *
 * @since 2007-01-18
 */
@SuppressWarnings("rawtypes")
public class SortHashtable implements Comparator {

    private static final String COLL_DATE_DATE = "collDate";

    public SortHashtable() {
    }

    /**
     * Compares two Map or Hashtable objects by their "collDate" values in descending order.
     *
     * @param object Object the first map to compare
     * @param object0 Object the second map to compare
     * @return int negative if the first date is after the second, positive if before, zero if equal
     * @throws IllegalArgumentException if either object is not a Map/Hashtable or contains
     *         a non-Date value for the "collDate" key
     */
    public int compare(Object object, Object object0) {
        int ret = 0;

        Date date1 = getDate(object);
        Date date2 = getDate(object0);
        if (date1.after(date2)) {
            ret = -1;
        } else if (date1.before(date2)) {
            ret = 1;
        }
        return ret;
    }

    private Date getDate(Object object) {
        Object result = null;
        if (Map.class.isAssignableFrom(object.getClass())) {
            result = ((Map) object).get(COLL_DATE_DATE);
        } else if (Hashtable.class.isAssignableFrom(object.getClass())) {
            result = ((Hashtable) object).get(COLL_DATE_DATE);
        } else {
            throw new IllegalArgumentException("Unsupported map type " + object.getClass().getName());
        }

        if (result == null) {
            return null;
        }

        if (result instanceof Date) {
            return (Date) result;
        }

        throw new IllegalArgumentException("Invalid value type for value \"" + COLL_DATE_DATE + "\". Expected "
                + Date.class.getName() + " but got " + result.getClass().getName() + " ( with value " + result + ")");
    }
}
