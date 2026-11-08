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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class WLWaitingListNameUtil {

    private static WaitingListNameDao nameDao = SpringUtils.getBean(WaitingListNameDao.class);
    private static WaitingListDao dao = SpringUtils.getBean(WaitingListDao.class);


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
     * Updates the current name through the existing waiting-list DAOs.
     * Null inputs, identifier {@code "0"}, or empty names/groups leave the record unchanged.
     *
     * @param wlNameId String identifier of the waiting-list name to update
     * @param wlName String replacement name
     * @param groupNo String group used to check for a duplicate name
     * @param providerNo String provider identifier required by the legacy validation contract
     * @throws Exception when the proposed name already exists in the group
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
