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
 */

package io.github.carlos_emr.carlos.waitinglist;

import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/*
 * This class is an interface with the file WEB-INF/classes
 * It is a singleton class. Do not instaciate it, use the method getInstance().
 * Every time that the properties file changes, tomcat must be restarted.
 */
public class WaitingList {

    private WaitingList() {

    }

    /**
     * @return WaitingList the instance of WaitingList
     */
    public static WaitingList getInstance() {
        return new WaitingList();
    }

    public boolean checkWaitingListTable() {
        WaitingListNameDao dao = SpringUtils.getBean(WaitingListNameDao.class);
        long count = dao.countActiveWatingListNames();
        return count > 0;
    }

    public boolean getFound() {
        return checkWaitingListTable();
    }
}
