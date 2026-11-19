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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class WLWaitingListUtil {

    /** Serializes all waiting-list updates, including position changes. */
    private static final Object MUTATION_LOCK = new Object();

    /**
     * Why a waiting-list note cannot be saved: it is longer than {@code waitingList.note} holds
     * ({@link WaitingList#validateNoteLength}, counted in Unicode code points). Under the packaged
     * empty {@code sql_mode} a longer note was cut with only a warning (#6141). Callers check this
     * first and show the refusal on their own page; every writer below refuses such a note too.
     *
     * @param note the note as it would be stored; may be {@code null}
     * @return a message naming the field and the limit, never the note; empty when it fits
     */
    public static List<String> noteLengthErrors(String note) {
        return WaitingList.validateNoteLength(note);
    }

    /** Refuses an over-long note before any waiting-list row is read or changed. */
    private static void requireNoteFits(String note) {
        List<String> errors = noteLengthErrors(note);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
    }


    // Modified this method in Feb 2007 to ensure that all records cannot be deleted except hidden.
    public static void removeFromWaitingList(String waitingListID, String demographicNo) {
        synchronized (MUTATION_LOCK) {
            removeEntries(waitingListID, demographicNo);
        }
    }

    private static void removeEntries(String waitingListID, String demographicNo) {
        MiscUtils.getLogger().debug("WLWaitingListUtil.removeFromWaitingList(): removing waiting list: " + waitingListID + " for patient " + demographicNo);

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        for (WaitingList wl : dao.findByWaitingListIdAndDemographicId(ConversionUtils.fromIntString(waitingListID), ConversionUtils.fromIntString(demographicNo))) {
            wl.setHistory(true);
            dao.merge(wl);
        }
        repositionEntries(waitingListID);
    }

    public static void add2WaitingList(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        synchronized (MUTATION_LOCK) {
            addEntry(waitingListID, waitingListNote, demographicNo, onListSince);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static void addEntry(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        requireNoteFits(waitingListNote);
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
        repositionEntries(waitingListID);
    }

    /*
     * This method adds the Waiting List note to the same position in the waitingList table but do not delete previous ones - later on DisplayWaitingList.jsp will display only the most current Waiting List Note record.
     *
     * This is a no-op when no list was named (id "0") or the patient has no entry on the
     * named list. The patient edit form can also use a historical entry to put the patient
     * back on the list.
     */
    public static void updateWaitingListRecord(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        recordNote(waitingListID, waitingListNote, demographicNo, onListSince, false);
    }

    /**
     * {@link #updateWaitingListRecord} for a caller that must not put a patient back on a list: the
     * note is recorded only when the patient has a current entry on the named list. Entries kept
     * as history after a removal do not count, so an update prepared before the removal is
     * reported instead of undoing it (#5298). The caller holds the patient row lock in its
     * transaction. The waiting-list rows are also locked through commit because this write
     * repositions the entire list.
     *
     * @return {@code true} when the note was recorded; {@code false} when nothing was written
     *         because no list was named (id "0") or the patient is not on the named list now
     */
    public static boolean updateCurrentWaitingListRecord(String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        return recordNote(waitingListID, waitingListNote, demographicNo, onListSince, true);
    }

    private static boolean recordNote(String waitingListID, String waitingListNote, String demographicNo, String onListSince, boolean currentEntryRequired) {
        synchronized (MUTATION_LOCK) {
            return recordEntryNote(waitingListID, waitingListNote, demographicNo, onListSince, currentEntryRequired);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean recordEntryNote(String waitingListID, String waitingListNote, String demographicNo, String onListSince, boolean currentEntryRequired) {
        requireNoteFits(waitingListNote);
        MiscUtils.getLogger().debug("WLWaitingListUtil.updateWaitingListRecord(): waitingListID: " + waitingListID + " for patient " + demographicNo);
        boolean isWatingIdSet = !waitingListID.equalsIgnoreCase("0") && !demographicNo.equalsIgnoreCase("0");
        if (!isWatingIdSet) return false;

        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        int listId = ConversionUtils.fromIntString(waitingListID);
        Integer patientId = ConversionUtils.fromIntString(demographicNo);
        // A locking read sees the committed current rows even when the REST transaction
        // established a repeatable-read snapshot before waiting for the patient lock.
        List<WaitingList> waitingLists = currentEntryRequired
                ? dao.findByWaitingListIdForUpdate(listId).stream()
                        .filter(entry -> patientId.equals(entry.getDemographicNo())).toList()
                : dao.findByWaitingListIdAndDemographicId(listId, patientId);
        // Not on this list: the patient edit form relies on this being a no-op (it then offers the
        // add), so it stays one, but it is reported, because a caller that was asked to save this
        // note has not saved it.
        if (waitingLists.isEmpty()) return false;
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
        wl.setListId(listId);
        wl.setDemographicNo(patientId);
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
        if (currentEntryRequired) {
            repositionEntries(dao, dao.findByWaitingListIdForUpdate(listId));
        } else {
            repositionEntries(waitingListID);
        }
        return true;
    }

    /*
     * This method adds the Waiting List note to the same position in the waitingList table but do not delete previous ones - later on DisplayWaitingList.jsp will display only the most current Waiting List Note record.
     */
    public static void updateWaitingList(String id, String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        synchronized (MUTATION_LOCK) {
            updateEntry(id, waitingListID, waitingListNote, demographicNo, onListSince);
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static void updateEntry(String id, String waitingListID, String waitingListNote, String demographicNo, String onListSince) {
        requireNoteFits(waitingListNote);
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

    /** Shares the mutation monitor, including when called directly by an action. */
    public static void rePositionWaitingList(String waitingListID) {
        synchronized (MUTATION_LOCK) {
            repositionEntries(waitingListID);
        }
    }

    private static void repositionEntries(String waitingListID) {
        WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);
        repositionEntries(dao, dao.findByWaitingListId(ConversionUtils.fromIntString(waitingListID)));
    }

    private static void repositionEntries(WaitingListDao dao, List<WaitingList> entries) {
        int i = 1;
        for (WaitingList waitingList : entries) {
            waitingList.setPosition(i);
            dao.merge(waitingList);
            i++;
        }
    }
}
