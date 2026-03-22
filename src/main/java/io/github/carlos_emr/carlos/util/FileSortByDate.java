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


package io.github.carlos_emr.carlos.util;

import java.io.File;
import java.util.Comparator;

/**
 * Comparator that sorts {@link File} objects by their last-modified date in
 * descending order (most recently modified first).
 *
 * @since 2001-01-01
 */
public class FileSortByDate implements Comparator {

    /**
     * Creates a new instance of FileSortByDate.
     */
    public FileSortByDate() {
    }

    /**
     * Compares two {@link File} objects by last-modified timestamp in descending order.
     *
     * @param object Object the first File to compare
     * @param object0 Object the second File to compare
     * @return int a negative value if the first file is newer, positive if older, zero if equal
     */
    public int compare(Object object, Object object0) {
        File f1 = (File) object;
        File f2 = (File) object0;

        long f1LastMod = f1.lastModified();
        long f2LastMod = f2.lastModified();

        if (f1LastMod < f2LastMod) {
            return 1;
        } else if (f2LastMod < f1LastMod) {
            return -1;
        }
        return 0;

    }


}
