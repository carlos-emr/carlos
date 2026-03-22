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

package io.github.carlos_emr.carlos.waitinglist;

import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Provides access to waiting list existence checks within the EMR system.
 *
 * <p>This class acts as a facade over the {@link WaitingListNameDao} to determine
 * whether any active waiting lists are configured. Although originally designed as
 * a singleton, each call to {@link #getInstance()} creates a new instance.</p>
 *
 * @since 2026-03-17
 */
public class WaitingList {

    private WaitingList() {

    }

    /**
     * Creates and returns a new instance of {@code WaitingList}.
     *
     * @return WaitingList a new instance of this class
     */
    public static WaitingList getInstance() {
        return new WaitingList();
    }

    /**
     * Checks whether any active waiting list names exist in the database.
     *
     * @return {@code true} if at least one active waiting list name exists, {@code false} otherwise
     */
    public boolean checkWaitingListTable() {
        WaitingListNameDao dao = SpringUtils.getBean(WaitingListNameDao.class);
        long count = dao.countActiveWatingListNames();
        return count > 0;
    }

    /**
     * Convenience method that delegates to {@link #checkWaitingListTable()}.
     *
     * @return {@code true} if active waiting lists exist, {@code false} otherwise
     */
    public boolean getFound() {
        return checkWaitingListTable();
    }
}
