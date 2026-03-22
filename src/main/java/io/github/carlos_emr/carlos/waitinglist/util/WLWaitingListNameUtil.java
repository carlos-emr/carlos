/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.waitinglist.util;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Utility class for managing waiting list name definitions (create, update, remove).
 *
 * <p>Provides static methods for CRUD operations on {@link WaitingListName} entities.
 * Removal is a soft-delete that sets the {@code is_history} flag to "Y".
 * Duplicate name checks are enforced within the same provider group before
 * creation or renaming.</p>
 *
 * @since 2026-03-17
 */
public class WLWaitingListNameUtil {

    private static WaitingListNameDao nameDao = SpringUtils.getBean(WaitingListNameDao.class);
    private static WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);


    /**
     * Soft-deletes a waiting list name by setting its {@code is_history} flag to "Y".
     *
     * <p>Refuses to remove a waiting list name that still has patients assigned to it,
     * throwing an exception with the message "wlNameUsed" in that case.</p>
     *
     * @param wlNameId String the ID of the waiting list name to remove
     * @param groupNo  String the provider group number (used for logging)
     * @throws Exception with message "wlNameUsed" if the waiting list still has patients
     */
    static public void removeFromWaitingListName(String wlNameId, String groupNo)
            throws Exception {
        if (wlNameId == null || groupNo == null) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/removeFromWaitingListName(): wlName or groupNo is null");
            return;
        }
        MiscUtils.getLogger().debug("WLWaitingListNameUtil/removeFromWaitingListName(): waiting list name: " + wlNameId +
                " for groupNo " + groupNo);


        boolean isUsed = isWaitingListNameBeingUsed(wlNameId);
        if (isUsed) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/removeFromWaitingListName(): Waiting list name is being used.");
            throw new Exception("wlNameUsed");
        }

        //update the list and set is_history = 'Y'     
        WaitingListName w = nameDao.find(Integer.parseInt(wlNameId));
        w.setIsHistory("Y");
        dao.merge(w);


        return;
    }

    /**
     * Creates a new waiting list name definition for the given provider group.
     *
     * <p>Checks for duplicate names within the group before persisting.
     * Silently returns without action if required parameters are null or empty.</p>
     *
     * @param wlName     String the name of the new waiting list
     * @param groupNo    String the provider group number
     * @param providerNo String the provider number of the creator
     * @throws Exception with message "wlNameExists" if the name already exists in the group
     */
    static public void createWaitingListName(String wlName, String groupNo, String providerNo)
            throws Exception {

        if (wlName == null || groupNo == null ||
                wlName.trim().length() <= 0 || groupNo.trim().length() <= 0) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/createWaitingListName(): " +
                    " wlName or groupNo or providerNo is null");
            return;
        }
        MiscUtils.getLogger().debug("WLWaitingListNameUtil/createWaitingListName(): waiting list name: " + wlName +
                " for groupNo: " + groupNo + "/ providerNo: " + providerNo);


        List<WaitingListName> wlns = getWaitingListNameRecords(wlName, groupNo);

        boolean isExist = isWaitingListNameExist(wlns);

        if (isExist) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/createWaitingListName(): The WL name already exists.");
            throw new Exception("wlNameExists");
        }

        WaitingListName wln = new WaitingListName();
        wln.setName(wlName);
        wln.setGroupNo(groupNo);
        wln.setProviderNo(providerNo);
        wln.setCreateDate(new Date());
        wln.setIsHistory("N");
        nameDao.persist(wln);


        return;
    }

    /**
     * Renames an existing waiting list name definition.
     *
     * <p>Checks for duplicate names within the group before updating.
     * Silently returns without action if required parameters are null or empty,
     * or if {@code wlNameId} is "0".</p>
     *
     * <p>Note: The original design preserved historical records rather than
     * deleting them, with the JSP displaying only the most current record.</p>
     *
     * @param wlNameId   String the ID of the waiting list name to rename
     * @param wlName     String the new name to assign
     * @param groupNo    String the provider group number for duplicate checking
     * @param providerNo String the provider number performing the update
     * @throws Exception with message "wlNameExists" if the new name already exists in the group
     */
    static public void updateWaitingListName(String wlNameId, String wlName, String groupNo, String providerNo)
            throws Exception {

        if (wlNameId == null || wlName == null || groupNo == null || providerNo == null ||
                wlNameId.equalsIgnoreCase("0") || wlName.length() <= 0 || groupNo.length() <= 0) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/updateWaitingListName(): " +
                    " wlNameId or wlName or groupNo or providerNo is null");
            return;
        }

        MiscUtils.getLogger().debug("WLWaitingListNameUtil/updateWaitingListName(): wlNameId/wlName = " +
                wlNameId + "/" + wlName);


        List<WaitingListName> wlns = getWaitingListNameRecords(wlName, groupNo);
        boolean isExist = isWaitingListNameExist(wlns);

        if (isExist) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/createWaitingListName(): The WL name already exists.");
            throw new Exception("wlNameExists");
        }

        WaitingListName wln = nameDao.find(Integer.parseInt(wlNameId));
        if (wln != null) {
            wln.setName(wlName);
            nameDao.merge(wln);
        }

        return;
    }


    static private boolean isWaitingListNameBeingUsed(String wlNameId) {

        if (wlNameId == null) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/isWaitingListNameBeingUsed(): db or rs or wlNameId is null");
            return true;
        }

        List<WaitingList> wls = dao.findByWaitingListId(Integer.parseInt(wlNameId));
        if (wls.size() > 0)
            return true;


        return false;
    }

    static private boolean isWaitingListNameExist(List<WaitingListName> wlns) {
        if (wlns.size() > 0)
            return true;
        return false;
    }

    static private List<WaitingListName> getWaitingListNameRecords(String wlName, String groupNo) {
        if (wlName == null || groupNo == null) {
            MiscUtils.getLogger().debug("WLWaitingListNameUtil/getWaitingListNameRecords(): db or rs or wlName or groupNo is null");
            return null;
        }

        List<WaitingListName> results = nameDao.findCurrentByNameAndGroup(wlName, groupNo);

        return results;
    }

}
