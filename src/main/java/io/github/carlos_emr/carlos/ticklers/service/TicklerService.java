//CHECKSTYLE:OFF
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

package io.github.carlos_emr.carlos.ticklers.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.PaginationQuery;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.ticklers.web.TicklerQuery;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.log.LogAction;

@Component
public class TicklerService {
    @Autowired
    private TicklersDao TicklerDao;

    /**
     * Use to get ticklers count for pagination display
     *
     * @param paginationQuery
     * @return
     */
    public int getTicklersCount(PaginationQuery paginationQuery) {
        return this.TicklerDao.getTicklersCount(paginationQuery);
    }

    /**
     * List ticklers
     *
     * @param paginationQuery
     * @return
     */
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, PaginationQuery paginationQuery) {
        TicklerQuery query = (TicklerQuery) paginationQuery;

        List<Tickler> results = TicklerDao.getTicklers(query);
        //--- log action ---
        if (results.size() > 0) {
            String resultIds = Tickler.getIdsAsStringList(results);
            LogAction.addLogSynchronous(loggedInInfo, "TicklerService.getTicklers", "ids returned=" + resultIds);
        }

        return results;
    }
}
