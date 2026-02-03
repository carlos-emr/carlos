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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.inbox.InboxManagerQuery;
import io.github.carlos_emr.carlos.inbox.InboxManagerResponse;
import io.github.carlos_emr.carlos.managers.InboxManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.InboxResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.InboxTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

@Path("/inbox")
@Component("inboxService")
@Consumes(MediaType.APPLICATION_JSON)
public class InboxService extends AbstractServiceImpl {

    @Autowired
    private InboxManager inboxManager;

    @GET
    @Path("/mine")
    @Produces("application/json")
    public InboxResponse getMyUnacknowlegedReports(@QueryParam("limit") int limit) {

        LoggedInInfo loggedInInfo = getLoggedInInfo();
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        InboxManagerQuery query = new InboxManagerQuery();
        query.setProviderNo(providerNo);
        query.setSearchProviderNo(providerNo);
        query.setStatus("N");
        query.setScannedDocStatus("I");
        query.setPage(0);
        query.setPageSize(20);
        query.setView("all");
        query.setPatientFirstName("");
        query.setPatientLastName("");
        query.setPatientHIN("");


        InboxManagerResponse response = inboxManager.getInboxResults(loggedInInfo, query);

        List<LabResultData> labDocs = response.getLabdocs();
        List<InboxTo1> responseItems = new ArrayList<InboxTo1>();

        for (LabResultData result : labDocs) {
            InboxTo1 inboxItem = new InboxTo1();
            String segmentID = result.getSegmentID();


            String discipline = result.isDocument() ? result.description == null ? "" : result.description : result.getDisciplineDisplayString();
            String status = ((result.isReportCancelled()) ? "Cancelled" : result.isFinal() ? "Final" : "Partial");

            inboxItem.setId(Integer.parseInt(segmentID));
            inboxItem.setDemographicName(result.getPatientName());
            inboxItem.setDemographicNo(result.getLabPatientId() != null ? Integer.parseInt(result.getLabPatientId()) : null);
            inboxItem.setDiscipline(discipline);
            inboxItem.setDateReceived(result.getDateTime() + (result.isDocument() ? " / " + result.lastUpdateDate : ""));
            inboxItem.setPriority(result.getPriority());
            inboxItem.setStatus(status);
            inboxItem.setHin(result.getHealthNumber());

            responseItems.add(inboxItem);

            if (responseItems.size() > limit) {
                continue;
            }

        }

        InboxResponse resp = new InboxResponse();
        resp.setContent(responseItems);
        resp.setLimit(limit);
        resp.setOffset(0);
        resp.setTimestamp(new Date());
        resp.setTotal(getMyUnacknowlegedReportsCount());

        return resp;
    }

    @GET
    @Path("/mine/count")
    public int getMyUnacknowlegedReportsCount() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        int count = dao.findByProviderNo(providerNo, "N").size();

        return count;
    }
}
