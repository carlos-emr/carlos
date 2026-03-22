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


package io.github.carlos_emr.carlos.waitinglist.bean;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Loads and provides access to the set of waiting list name definitions for a provider group.
 *
 * <p>On construction, queries the {@link WaitingListNameDao} for all active (non-historical)
 * waiting list names belonging to the specified group, and populates both a list of
 * {@link WLWaitingListNameBean} objects and a parallel list of plain name strings.</p>
 *
 * @since 2026-03-17
 */
public class WLWaitingListNameBeanHandler {

    List<WLWaitingListNameBean> waitingListNameList = new ArrayList<WLWaitingListNameBean>();
    List<String> waitingListNames = new ArrayList<String>();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //  private MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    private WaitingListNameDao waitingListNameDao = SpringUtils.getBean(WaitingListNameDao.class);
    //  private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    /**
     * Constructs the handler and loads all active waiting list names for the given group.
     *
     * @param groupNo    String the provider group number to filter by
     * @param providerNo String the provider number (currently unused in query, retained for compatibility)
     */
    public WLWaitingListNameBeanHandler(String groupNo, String providerNo) {
        init(groupNo, providerNo);
    }

    /**
     * Initializes the handler by loading all active waiting list names for the group.
     *
     * @param groupNo    String the provider group number to filter by
     * @param providerNo String the provider number (reserved for future use)
     * @return {@code true} always (retained for backward compatibility)
     */
    public boolean init(String groupNo, String providerNo) {
        List<WaitingListName> wlNames = null;

        wlNames = waitingListNameDao.findCurrentByGroup(groupNo);

        for (WaitingListName tmp : wlNames) {
            WLWaitingListNameBean wLBean =
                    new WLWaitingListNameBean(String.valueOf(tmp.getId()), tmp.getName(), tmp.getGroupNo(), tmp.getProviderNo(), formatter.format(tmp.getCreateDate()));

            waitingListNameList.add(wLBean);
            waitingListNames.add(tmp.getName());
        }


        return true;
    }

    /**
     * Returns the list of waiting list name beans with full metadata.
     *
     * @return List&lt;WLWaitingListNameBean&gt; the waiting list name definitions
     */
    public List<WLWaitingListNameBean> getWaitingListNameList() {
        return waitingListNameList;
    }

    public List<String> getWaitingListNames() {
        return waitingListNames;
    }

}
