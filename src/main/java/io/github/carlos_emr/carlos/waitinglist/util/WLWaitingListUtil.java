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

package io.github.carlos_emr.carlos.waitinglist.util;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Utility class for managing individual patient entries on waiting lists.
 *
 * <p>Provides synchronized static methods for adding, removing, and updating
 * patient entries within a waiting list, as well as recalculating sequential
 * positions after modifications. Removal is a soft-delete that sets the
 * {@code is_history} flag, preserving historical records for auditing.</p>
 *
 * <p>All mutation methods are synchronized to prevent concurrent modification
 * of waiting list positions.</p>
 *
 * @since 2026-03-17
 */
public class WLWaitingListUtil {


    /**
     * Soft-deletes a patient from a waiting list by marking their entries as historical,
     * then recalculates positions for the remaining patients.
     *
     * @param waitingListID String the waiting list identifier
     * @param demographicNo String the patient demographic number to remove
     */
    // Modified this method in Feb 2007 to ensure that all records cannot be deleted except hidden.
    static public synchronized void removeFromWaitingList(String waitingListID, String demographicNo) {
        MiscUtils.getLogger().debug("WLWaitingListUtil.removeFromWaitingList(): removing waiting list: " + waitingListID + " for patient " + demographicNo);

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        for (WaitingList wl : dao.findByWaitingListIdAndDemographicId(ConversionUtils.fromIntString(waitingListID), ConversionUtils.fromIntString(demographicNo))) {
            wl.setHistory(true);
            dao.merge(wl);
        }
        rePositionWaitingList(waitingListID);
    }

    /**
     * Adds a patient to a waiting list at the next available position.
     *
     * <p>The patient is placed after the current last position. If {@code onListSince}
     * is null or empty, the current date is used. After insertion, positions are
     * recalculated for the entire list.</p>
     *
     * @param waitingListID   String the waiting list identifier
     * @param waitingListNote String a note for this waiting list entry
     * @param demographicNo   String the patient demographic number
     * @param onListSince     String the date the patient was placed on the list (yyyy-MM-dd),
     *                        or null/empty to use today's date
     */
    static public synchronized void add2WaitingList(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        MiscUtils.getLogger().debug("WLWaitingListUtil.add2WaitingList(): adding to waitingList: " + waitingListID + " for patient " + demographicNo);

        boolean emptyIds = waitingListID.equalsIgnoreCase("0") || demographicNo.equalsIgnoreCase("0");
        if (emptyIds) {
            MiscUtils.getLogger().debug("Ids are not proper - exiting");
            return;
        }

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        int maxPosition = dao.getMaxPosition(ConversionUtils.fromIntString(waitingListID));

        WaitingList list = new WaitingList();
        list.setListId(ConversionUtils.fromIntString(waitingListID));
        list.setDemographicNo(ConversionUtils.fromIntString(demographicNo));
        list.setNote(waitingListNote);
        if (onListSince == null || onListSince.length() <= 0) {
            list.setOnListSince(new Date());
        } else {
            list.setOnListSince(ConversionUtils.fromDateString(onListSince));
        }
        list.setPosition(maxPosition + 1);
        list.setHistory(false);

        dao.persist(list);

        // update the waiting list positions
        rePositionWaitingList(waitingListID);
    }

    /**
     * Updates a patient's waiting list entry by archiving all previous records and
     * creating a new record at the same position.
     *
     * <p>Previous entries are marked as historical (soft-deleted) rather than physically
     * deleted, preserving an audit trail. The JSP display layer shows only the most
     * current non-historical record.</p>
     *
     * @param waitingListID   String the waiting list identifier
     * @param waitingListNote String the updated note text
     * @param demographicNo   String the patient demographic number
     * @param onListSince     String the date the patient was placed on the list (yyyy-MM-dd),
     *                        or null/empty to use today's date
     */
    static public synchronized void updateWaitingListRecord(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        MiscUtils.getLogger().debug("WLWaitingListUtil.updateWaitingListRecord(): waitingListID: " + waitingListID + " for patient " + demographicNo);
        boolean isWatingIdSet = !waitingListID.equalsIgnoreCase("0") && !demographicNo.equalsIgnoreCase("0");
        if (!isWatingIdSet) return;

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        List<WaitingList> waitingLists = dao.findByWaitingListIdAndDemographicId(ConversionUtils.fromIntString(waitingListID), ConversionUtils.fromIntString(demographicNo));
        if (waitingLists.isEmpty()) return;

        long pos = 1;
        for (WaitingList wl : waitingLists) {
            pos = wl.getPosition();
        }

        // set all previous records 'is_history' fielf to 'N' --> to keep as record but never display
        for (WaitingList wl : waitingLists) {
            wl.setHistory(true);
            dao.merge(wl);
        }

        WaitingList wl = new WaitingList();
        wl.setListId(ConversionUtils.fromIntString(waitingListID));
        wl.setDemographicNo(ConversionUtils.fromIntString(demographicNo));
        wl.setNote(waitingListNote);
        wl.setPosition(pos);
        if (onListSince == null || onListSince.length() <= 0) {
            wl.setOnListSince(new Date());
        } else {
            wl.setOnListSince(ConversionUtils.fromDateString(onListSince));
        }
        wl.setHistory(false);

        dao.saveEntity(wl);

        // update the waiting list positions
        rePositionWaitingList(waitingListID);
    }

    /**
     * Updates a specific waiting list entry by its record ID, modifying the list assignment,
     * note, and enrollment date in place.
     *
     * <p>Unlike {@link #updateWaitingListRecord}, this method directly modifies the
     * existing record rather than creating a new one. Silently returns if the ID
     * is not set or the record is not found.</p>
     *
     * @param id              String the unique record ID of the waiting list entry
     * @param waitingListID   String the waiting list identifier to assign
     * @param waitingListNote String the updated note text
     * @param demographicNo   String the patient demographic number
     * @param onListSince     String the date the patient was placed on the list (yyyy-MM-dd)
     */
    static public synchronized void updateWaitingList(String id, String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        MiscUtils.getLogger().debug("WLWaitingListUtil.updateWaitingList(): waitingListID: " + waitingListID + " for patient " + demographicNo);
        boolean idsSet = !waitingListID.equalsIgnoreCase("0") && !demographicNo.equalsIgnoreCase("0");
        if (!idsSet) {
            MiscUtils.getLogger().debug("Ids are not set - exiting");
            return;
        }

        boolean wlIdsSet = (id != null && !id.equals(""));
        if (!wlIdsSet) {
            MiscUtils.getLogger().debug("Waiting list id is not set");
            return;
        }

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        WaitingList waitingListEntry = dao.find(ConversionUtils.fromIntString(id));
        if (waitingListEntry == null) {
            MiscUtils.getLogger().debug("Unable to fetch waiting list with id " + id);
            return;
        }

        waitingListEntry.setListId(ConversionUtils.fromIntString(waitingListID));
        waitingListEntry.setNote(waitingListNote);
        waitingListEntry.setOnListSince(ConversionUtils.fromDateString(onListSince));

        dao.merge(waitingListEntry);
    }

    /**
     * Recalculates sequential position numbers (1-based) for all active entries
     * in the specified waiting list.
     *
     * @param waitingListID String the waiting list identifier to reposition
     */
    public static void rePositionWaitingList(String waitingListID) {
        int i = 1;
        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        for (WaitingList waitingList : dao.findByWaitingListId(ConversionUtils.fromIntString(waitingListID))) {
            waitingList.setPosition(i);
            dao.merge(waitingList);
            i++;
        }
    }
}
