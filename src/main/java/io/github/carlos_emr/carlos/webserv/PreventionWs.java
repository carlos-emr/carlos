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

package io.github.carlos_emr.carlos.webserv;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.PreventionTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class PreventionWs extends AbstractWs {
    @Autowired
    private PreventionManager preventionManager;

    public PreventionTransfer getPrevention(Integer preventionId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();

        Prevention prevention = preventionManager.getPrevention(loggedInInfo, preventionId);

        if (prevention != null) {
            List<PreventionExt> preventionExts = preventionManager.getPreventionExtByPrevention(loggedInInfo, prevention.getId());
            return (PreventionTransfer.toTransfer(prevention, preventionExts));
        }

        return (null);
    }

    public PreventionTransfer[] getPreventionsUpdatedAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prevention> preventions = preventionManager.getUpdatedAfterDate(loggedInInfo, updatedAfterThisDateExclusive, itemsToReturn);
        return (PreventionTransfer.getTransfers(loggedInInfo, preventions));
    }

    public PreventionTransfer[] getPreventionsByProgramProviderDemographicDate(Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prevention> preventions = preventionManager.getPreventionsByProgramProviderDemographicDate(getLoggedInInfo(), programId, providerNo, demographicId, updatedAfterThisDateExclusive, itemsToReturn);
        return (PreventionTransfer.getTransfers(loggedInInfo, preventions));
    }

    public PreventionTransfer[] getPreventionsByDemographicIdAfter(@WebParam(name = "lastUpdate") Calendar lastUpdate, @WebParam(name = "demographicId") Integer demographicId) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        List<Prevention> preventions = preventionManager.getByDemographicIdUpdatedAfterDate(loggedInInfo, demographicId, lastUpdate.getTime());
        return (PreventionTransfer.getTransfers(loggedInInfo, preventions));
    }

}
