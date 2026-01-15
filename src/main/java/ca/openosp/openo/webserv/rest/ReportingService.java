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
package ca.openosp.openo.webserv.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.EFormReportToolDao;
import ca.openosp.openo.commn.model.PreventionReport;
import ca.openosp.openo.commn.dao.PreventionReportDao;
import ca.openosp.openo.commn.model.DemographicSets;
import ca.openosp.openo.commn.model.EFormReportTool;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.DemographicSetsManager;
import ca.openosp.openo.managers.EFormReportToolManager;
import ca.openosp.openo.prev.reports.Report;
import ca.openosp.openo.prev.reports.ReportBuilder;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.web.PatientListApptBean;
import ca.openosp.openo.web.PatientListApptItemBean;
import ca.openosp.openo.webserv.rest.conversion.EFormReportToolConverter;
import ca.openosp.openo.webserv.rest.to.AbstractSearchResponse;
import ca.openosp.openo.webserv.rest.to.GenericRESTResponse;
import ca.openosp.openo.webserv.rest.to.model.EFormReportToolTo1;
import ca.openosp.openo.webserv.rest.to.model.MenuItemTo1;
import ca.openosp.openo.webserv.rest.to.model.PreventionSearchTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/reporting/")
@Component
@Consumes(MediaType.APPLICATION_JSON)
public class ReportingService extends AbstractServiceImpl {
    private static Logger logger = MiscUtils.getLogger();

    //private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    DemographicSetsManager demographicSetsManager;

    @Autowired
    DemographicManager demographicManager;

    @Autowired
    EFormReportToolManager eformReportToolManager;

    @Autowired
    PreventionReportDao preventionReportDao;

    @GET
    @Path("/demographicSets/list")
    @Produces("application/json")
    public AbstractSearchResponse<String> listDemographicSets() {
        AbstractSearchResponse<String> response = new AbstractSearchResponse<String>();

        response.setContent(demographicSetsManager.getNames(getLoggedInInfo()));
        response.setTotal(response.getContent().size());

        return (response);
    }

    @GET
    @Path("/demographicSets/demographicSet/{name}")
    @Produces("application/json")
    public AbstractSearchResponse<DemographicSets> getDemographicSetByName(@PathParam("name") String name) {
        AbstractSearchResponse<DemographicSets> response = new AbstractSearchResponse<DemographicSets>();

        response.setContent(demographicSetsManager.getByName(getLoggedInInfo(), name));
        response.setTotal(response.getContent().size());

        return (response);
    }

    @POST
    @Path("/demographicSets/patientList")
    @Produces("application/json")
    @Consumes("application/json")
    public PatientListApptBean getAsPatientList(JsonNode json) {

        PatientListApptBean response = new PatientListApptBean();

        if (json.has("name") && json.get("name").asText().length() > 0) {

            for (DemographicSets demographicSet : demographicSetsManager.getByName(getLoggedInInfo(), json.get("name").asText())) {
                PatientListApptItemBean item = new PatientListApptItemBean();
                item.setDemographicNo(demographicSet.getDemographicNo());
                item.setName(demographicManager.getDemographicFormattedName(getLoggedInInfo(), item.getDemographicNo()));
                response.getPatients().add(item);
            }
        }

        return (response);
    }

    /**
     * EFromReportTool is a utility for taking a snapshot of key-value pair data from saved eforms
     * to a new table for easier querying. need _admin.eformreporttool sec object.
     *
     * @return
     */
    @GET
    @Path("/eformReportTool/list")
    @Produces("application/json")
    public AbstractSearchResponse<EFormReportToolTo1> eformReportToolList() {

        List<EFormReportTool> results = eformReportToolManager.findAll(getLoggedInInfo(), 0, EFormReportToolDao.MAX_LIST_RETURN_SIZE);

        EFormReportToolConverter converter = new EFormReportToolConverter(true, true);

        AbstractSearchResponse<EFormReportToolTo1> response = new AbstractSearchResponse<EFormReportToolTo1>();

        response.setContent(converter.getAllAsTransferObjects(getLoggedInInfo(), results));
        response.setTotal(response.getContent().size());

        return (response);
    }


    @POST
    @Path("/eformReportTool/add")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse addEFormReportTool(EFormReportToolTo1 json) {

        GenericRESTResponse response = new GenericRESTResponse();

        if (StringUtils.isEmpty(json.getName()) || json.getEformId() == 0) {
            response.setSuccess(false);
            response.setMessage("Need required fields");
            return response;
        }

        EFormReportToolConverter converter = new EFormReportToolConverter();

        eformReportToolManager.addNew(getLoggedInInfo(), converter.getAsDomainObject(getLoggedInInfo(), json));

        return (response);
    }

    @POST
    @Path("/eformReportTool/populate")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse populateEFormReportTool(EFormReportToolTo1 json) {

        GenericRESTResponse response = new GenericRESTResponse();

        eformReportToolManager.populateReportTable(getLoggedInInfo(), json.getId());

        return (response);
    }


    @POST
    @Path("/eformReportTool/remove")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse removeEFormReportTool(EFormReportToolTo1 json) {

        GenericRESTResponse response = new GenericRESTResponse();

        eformReportToolManager.remove(getLoggedInInfo(), json.getId());

        return (response);
    }

    @POST
    @Path("/eformReportTool/markLatest")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse markLatestEFormReportTool(EFormReportToolTo1 json) {

        GenericRESTResponse response = new GenericRESTResponse();

        eformReportToolManager.markLatest(getLoggedInInfo(), json.getId());

        return (response);
    }


    @POST
    @Path("/preventionReport/saveNew")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse saveNewPreventionReport(PreventionSearchTo1 preventionSearch) {
        GenericRESTResponse response = new GenericRESTResponse();

        //Next thing to do is to save the JSON object to the database
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonStr = mapper.writeValueAsString(preventionSearch);
            PreventionReport pr = new PreventionReport();
            pr.setReportName(preventionSearch.getReportName());
            pr.setJson(jsonStr);
            pr.setProviderNo(getLoggedInInfo().getLoggedInProviderNo());
            pr.setUuid(UUID.randomUUID().toString());
            preventionReportDao.persist(pr);
            response.setMessage("" + pr.getId());
            response.setSuccess(true);
        } catch (Exception e) {
            logger.error("error converting to STring");
            response.setSuccess(false);
        }
        return (response);
    }

    @GET
    @Path("/preventionReport/getList")
    @Produces("application/json")
    @Consumes("application/json")
    public List<MenuItemTo1> getPreventionReports() {
        List<MenuItemTo1> returnList = new ArrayList<MenuItemTo1>();
        List<PreventionReport> list = preventionReportDao.getPreventionReports();
        for (PreventionReport pr : list) {
            MenuItemTo1 item = new MenuItemTo1();
            item.setId(pr.getId());
            item.setLabel("(" + pr.getId() + ") " + pr.getReportName());
            returnList.add(item);
        }

        return (returnList);
    }


    @POST
    @Path("/preventionReport/runReport/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public javax.ws.rs.core.Response runPreventionReport(@PathParam("id") Integer id, JsonNode jSONObject) { // will need to change providers to an ojbect
        GenericRESTResponse response = new GenericRESTResponse();
        Report report = null;
        //Next thing to do is to save the JSON object to the database
        String providerNo = jSONObject.has("providerNo") ? jSONObject.get("providerNo").asText() : "";

        if (StringUtils.isEmpty(providerNo)) {
            providerNo = getLoggedInInfo().getLoggedInProviderNo();
        }

        PreventionReport pr = preventionReportDao.find(id);
        ObjectMapper mapper = new ObjectMapper();
        try {
            logger.info("pr: " + pr.getJson());
            PreventionSearchTo1 preventionSearchTo1 = mapper.readValue(pr.getJson(), PreventionSearchTo1.class);
            logger.info("preventionSearchTo1: " + preventionSearchTo1);
            ReportBuilder reportBuilder = new ReportBuilder();
            report = reportBuilder.runReport(getLoggedInInfo(), providerNo, preventionSearchTo1);
            if (!pr.isActive()) {
                report.setActive(false);
            }
        } catch (Exception e) {
            logger.error("Error parsing ", e);
        }

        logger.info("providers was " + providerNo);
        if (report == null) {
            javax.ws.rs.core.Response.status(268).entity("{\"Error\":\"Error building report\"}");
        }
        return javax.ws.rs.core.Response.ok(report).build();
    }

    @POST
    @Path("/preventionReport/getReport/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public javax.ws.rs.core.Response getPreventionReport(@PathParam("id") Integer id, JsonNode jSONObject) { // will need to change providers to an ojbect
        GenericRESTResponse response = new GenericRESTResponse();
        Report report = null;
        //Next thing to do is to save the JSON object to the database
        String providerNo = jSONObject.has("providerNo") ? jSONObject.get("providerNo").asText() : "";


        PreventionReport pr = preventionReportDao.find(id);
        ObjectMapper mapper = new ObjectMapper();
        try {
            logger.info("pr: " + pr.getJson());
            PreventionSearchTo1 preventionSearchTo1 = mapper.readValue(pr.getJson(), PreventionSearchTo1.class);
            return javax.ws.rs.core.Response.ok(preventionSearchTo1).build();
        } catch (Exception e) {
            logger.error("Error parsing ", e);
        }

        return javax.ws.rs.core.Response.status(268).entity("{\"Error\":\"Error get Search Config\"}").build();
    }

    @POST
    @Path("/preventionReport/dectivateReport/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    public javax.ws.rs.core.Response getPreventionReport(@PathParam("id") Integer id) { // will need to change providers to an ojbect
        GenericRESTResponse response = new GenericRESTResponse();

        PreventionReport pr = preventionReportDao.find(id);
        pr.setActive(false);
        preventionReportDao.merge(pr);

        return javax.ws.rs.core.Response.ok("{\"Message\":\"report deactivated\"}").build();
    }


}
