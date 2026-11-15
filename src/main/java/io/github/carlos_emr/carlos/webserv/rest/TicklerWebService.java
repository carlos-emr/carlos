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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.conversion.TicklerConverter;
import io.github.carlos_emr.carlos.webserv.rest.conversion.TicklerTextSuggestConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.TicklerResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.TicklerTextSuggestTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.LogSafe;

@Path("/tickler")
@Component("ticklerWebService")
@Consumes(MediaType.APPLICATION_JSON)
public class TicklerWebService extends AbstractServiceImpl {

    @Autowired
    private TicklerManager ticklerManager;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private ProgramManager2 programManager;


    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @POST
    @Path("/search")
    @Produces("application/json")
    @Consumes("application/json")
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TicklerResponse search(JsonNode json, @QueryParam("startIndex") int startIndex, @QueryParam("limit") int limit) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        CustomFilter cf = new CustomFilter(true);

        if (json.has("status")) {
            cf.setStatus(json.get("status").asText());
        }
        if (json.has("priority")) {
            cf.setPriority(json.get("priority").asText());
        }
        if (json.has("assignee")) {
            cf.setAssignee(json.get("assignee").asText());
        }
        if (json.has("demographicNo")) {
            cf.setDemographicNo(json.get("demographicNo").asText());
            requireFilterPatient(cf);
        }

        //this will need refactor...needs a manager layer and some useful methods.
        //basically if overdueOnly='property', I check Persona for what to return, this
        //avoids cliend needing to know their preferences and passing them back.
        if (json.has("overdueOnly") && "property".equals(json.get("overdueOnly").asText())) {
            UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
            String strVal = propDao.getStringValue(getCurrentProvider().getProviderNo(), "dashboard.expiredTicklersOnly");
            if (strVal != null && "true".equalsIgnoreCase(strVal)) {
                cf.setEndDate(new Date());
            }
            if (strVal != null && "false".equalsIgnoreCase(strVal)) {
                cf.setEndDate(null);
            }

            if (strVal == null) {
                cf.setEndDate(new Date());
            }

        }


        var page = authorizedPage(cf, startIndex, limit, false, false);
        List<Tickler> ticklers = page.rows();
        TicklerResponse result = new TicklerResponse();
        result.setTotal(page.total());

        TicklerConverter converter = new TicklerConverter();
        result.getContent().addAll(converter.getAllAsTransferObjects(getLoggedInInfo(), ticklers));


        return result;
    }

    @GET
    @Path("/mine")
    @Produces("application/json")
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TicklerResponse getMyTicklers(@QueryParam("limit") int limit) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        CustomFilter cf = new CustomFilter(true);
        cf.setAssignee(getLoggedInInfo().getLoggedInProviderNo());
        cf.setStatus("A");

        List<Tickler> ticklers = authorizedPage(cf, 0, limit, false, false).rows();

        TicklerResponse result = new TicklerResponse();
        result.setTotal(ticklers.size());
        TicklerConverter converter = new TicklerConverter();
        result.getContent().addAll(converter.getAllAsTransferObjects(getLoggedInInfo(), ticklers));


        return result;
    }

    @GET
    @Path("/ticklers")
    @Produces("application/json")
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public TicklerResponse getTicklerList() {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "r", null)) {
            throw new RuntimeException("Access Denied");
        }

        HttpServletRequest req = this.getHttpServletRequest();

        String strCount = req.getParameter("count");
        String strPage = req.getParameter("page");

        String serviceStartDate = req.getParameter("serviceStartDate");
        String serviceEndDate = req.getParameter("serviceEndDate");
        String status = req.getParameter("status");
        String taskAssignedTo = req.getParameter("taskAssignedTo");
        String mrp = req.getParameter("mrp");
        String creator = req.getParameter("creator");
        String priority = req.getParameter("priority");

        boolean includeLinks = Boolean.valueOf(req.getParameter("includeLinks"));
        boolean includeComments = Boolean.valueOf(req.getParameter("includeComments"));
        boolean includeUpdates = Boolean.valueOf(req.getParameter("includeUpdates"));
        boolean includeProgram = Boolean.valueOf(req.getParameter("includeProgram"));


        int count = Integer.parseInt(strCount);
        int page = Integer.parseInt(strPage);


        CustomFilter cf = new CustomFilter(true);

        if (serviceStartDate != null && !"".equals(serviceStartDate)) {
            serviceStartDate = serviceStartDate.startsWith("\"") ? serviceStartDate.substring(1, serviceStartDate.length() - 1) : serviceStartDate;
            try {
                cf.setStartDate(jakarta.xml.bind.DatatypeConverter.parseDateTime(serviceStartDate).getTime());
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Error parsing start date - " + serviceStartDate);
            }
        }
        if (serviceEndDate != null && !"".equals(serviceEndDate)) {
            serviceEndDate = serviceEndDate.startsWith("\"") ? serviceEndDate.substring(1, serviceEndDate.length() - 1) : serviceEndDate;
            try {
                cf.setEndDate(jakarta.xml.bind.DatatypeConverter.parseDateTime(serviceEndDate).getTime());
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Error parsing end date - " + serviceEndDate);
            }
        }

        cf.setStatus(status);

        cf.setPriority(priority);


        if (taskAssignedTo != null && !"".equals(taskAssignedTo)) {
            cf.setAssignee(taskAssignedTo);
        }

        if (creator != null && !"".equals(creator)) {
            cf.setProviderNo(creator);
        }

        if (mrp != null && !"".equals(mrp)) {
            cf.setMrp(mrp);
        }

        if (req.getParameter("demographicNo") != null) {
            cf.setDemographicNo(req.getParameter("demographicNo"));
            requireFilterPatient(cf);
        }

        TicklerResponse result = new TicklerResponse();


        if (page < 1 || count < 0) throw new jakarta.ws.rs.BadRequestException("Invalid page position or size");
        int offset;
        try { offset = Math.multiplyExact(page - 1, count); }
        catch (ArithmeticException invalid) { throw new jakarta.ws.rs.BadRequestException("Invalid page position or size"); }
        var permitted = authorizedPage(cf, offset, count, includeComments, includeUpdates);
        result.setTotal(permitted.total());
        List<Tickler> ticklers = permitted.rows();

        TicklerConverter converter = new TicklerConverter();
        converter.setIncludeLinks(includeLinks);
        converter.setIncludeComments(includeComments);
        converter.setIncludeUpdates(includeUpdates);
        converter.setIncludeProgram(includeProgram);

        result.getContent().addAll(converter.getAllAsTransferObjects(getLoggedInInfo(), ticklers));

        return result;
    }

    @POST
    @Path("/complete")
    @Produces("application/json")
    @Consumes("application/json")
    @org.springframework.transaction.annotation.Transactional
    public RestResponse<String> completeTicklers(JsonNode json) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "u", null)) {
            throw new RuntimeException("Access Denied");
        }


        MiscUtils.getLogger().debug("completeTicklers called, count={}", json != null && json.has("ticklers") ? json.get("ticklers").size() : 0);

        List<Integer> ticklerIds;
        try {
            ticklerIds = extractTicklerIds(json);
        } catch (IllegalArgumentException e) {
            return RestResponse.errorResponse(e.getMessage());
        }

        // Validate every stored patient before the first mutation in a bulk request.
        for (Integer ticklerNo : new java.util.TreeSet<>(ticklerIds)) requireStoredTicklerPatient(ticklerNo, "u");
        for (Integer ticklerNo : ticklerIds) {
            ticklerManager.completeTickler(getLoggedInInfo(), ticklerNo, getLoggedInInfo().getLoggedInProviderNo());
        }

        return RestResponse.successResponse(null);
    }

    @POST
    @Path("/delete")
    @Produces("application/json")
    @Consumes("application/json")
    @org.springframework.transaction.annotation.Transactional
    public RestResponse<String> deleteTicklers(JsonNode json) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "u", null)) {
            throw new RuntimeException("Access Denied");
        }

        MiscUtils.getLogger().debug("deleteTicklers called, count={}", json != null && json.has("ticklers") ? json.get("ticklers").size() : 0);

        List<Integer> ticklerIds;
        try {
            ticklerIds = extractTicklerIds(json);
        } catch (IllegalArgumentException e) {
            return RestResponse.errorResponse(e.getMessage());
        }

        // Validate every stored patient before the first mutation in a bulk request.
        for (Integer ticklerNo : new java.util.TreeSet<>(ticklerIds)) requireStoredTicklerPatient(ticklerNo, "u");
        for (Integer ticklerNo : ticklerIds) {
            ticklerManager.deleteTickler(getLoggedInInfo(), ticklerNo, getLoggedInInfo().getLoggedInProviderNo());
        }

        return RestResponse.successResponse(null);
    }

    /**
     * Extracts and validates the {@code ticklers} id array from a bulk request body.
     *
     * <p>The field must be present, a JSON array, and contain only integral values that
     * fit a 32-bit int. Reading ids with {@link JsonNode#asInt()} alone would let a missing
     * field NPE and silently coerce non-numeric values to {@code 0}, so malformed input
     * is rejected here with a clear message instead. {@link JsonNode#canConvertToInt()}
     * alone is not enough: it returns {@code true} for fractional values in int range
     * (e.g. {@code 1.9}), which {@link JsonNode#intValue()} would silently truncate to
     * {@code 1}, so {@link JsonNode#isIntegralNumber()} is checked first to reject
     * non-integral numbers.</p>
     *
     * @param json the request body
     * @return the parsed tickler ids (possibly empty)
     * @throws IllegalArgumentException if the field is missing, not an array, or holds a non-integer id
     */
    private List<Integer> extractTicklerIds(JsonNode json) {
        JsonNode ticklerIds = json.get("ticklers");
        if (ticklerIds == null || !ticklerIds.isArray()) {
            throw new IllegalArgumentException("ticklers must be an array of integer ids");
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode id : ticklerIds) {
            if (!id.isIntegralNumber() || !id.canConvertToInt()) {
                throw new IllegalArgumentException("ticklers must contain only integer ids");
            }
            ids.add(id.intValue());
        }
        return ids;
    }

    @POST
    @Path("/update")
    @Produces("application/json")
    @Consumes("application/json")
    @org.springframework.transaction.annotation.Transactional
    public RestResponse<String> updateTickler(JsonNode json) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "u", null)) {
            throw new RuntimeException("Access Denied");
        }

        MiscUtils.getLogger().debug("updateTickler called, id={}", LogSafe.sanitize(json != null && json.has("id") ? json.get("id").asText() : "null"));

        JsonNode identity = json == null ? null : json.get("id");
        if (identity == null || !identity.isIntegralNumber() || !identity.canConvertToInt()) {
            throw new jakarta.ws.rs.BadRequestException("Invalid tickler identifier");
        }
        Integer storedPatient = requireStoredTicklerPatient(identity.intValue(), "u");
        Tickler tickler = ticklerManager.getTickler(getLoggedInInfo(), identity.intValue());

        if (tickler == null) {
            throw new RuntimeException("Tickler not found");
        }

        if (!storedPatient.equals(tickler.getDemographicNo())) {
            throw new IllegalStateException("Tickler patient changed during authorization");
        }
        tickler.setTaskAssignedTo(json.get("taskAssignedTo").asText());

        tickler.setStatusAsChar(json.get("status").asText().charAt(0));

        tickler.setPriorityAsString(json.get("priority").asText());

        tickler.setMessage(json.get("message").asText());

        //tickler.setUpdateDate(new Date());

        String dt = json.get("serviceDate").asText();
        tickler.setServiceDate(jakarta.xml.bind.DatatypeConverter.parseDateTime(dt).getTime());

        boolean success = ticklerManager.updateTickler(getLoggedInInfo(), tickler);

        if (success) {

            if (json.has("ticklerComments")) {
                ArrayNode arr = (ArrayNode) json.get("ticklerComments");
                for (int x = 0; x < arr.size(); x++) {
                    ObjectNode c = (ObjectNode) arr.get(x);

                    if (c.has("newComment")) {
                        ticklerManager.addComment(getLoggedInInfo(), tickler.getId(), c.get("providerNo").asText(), c.get("message").asText());
                    }
                }
            }
            return RestResponse.successResponse(null);
        }

        rollbackFailedMutation();
        return RestResponse.errorResponse("Failed to update tickler");
    }

    @GET
    @Path("/textSuggestions")
    @Produces("application/json")
    public AbstractSearchResponse<TicklerTextSuggestTo1> getTextSuggestions() {

        AbstractSearchResponse<TicklerTextSuggestTo1> response = new AbstractSearchResponse<TicklerTextSuggestTo1>();

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "r", null)) {
            throw new RuntimeException("Access Denied");
        }
        List<TicklerTextSuggest> suggestions = ticklerManager.getActiveTextSuggestions(getLoggedInInfo());

        response.setContent(new TicklerTextSuggestConverter().getAllAsTransferObjects(getLoggedInInfo(), suggestions));
        response.setTotal(response.getContent().size());

        return response;
    }

    @POST
    @Path("/add")
    @Produces("application/json")
    @Consumes("application/json")
    @org.springframework.transaction.annotation.Transactional
    public RestResponse<String> addTickler(Tickler tickler) {

        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_tickler", "w", null)) {
            throw new RuntimeException("Access Denied");
        }

        if (tickler == null) throw new jakarta.ws.rs.BadRequestException("Tickler is required");
        io.github.carlos_emr.carlos.webserv.rest.util.PatientApiAccess.require(
                getLoggedInInfo(), securityInfoManager, "_tickler", "w", tickler.getDemographicNo());
        tickler.setUpdateDate(new Date());
        tickler.setCreator(getLoggedInInfo().getLoggedInProviderNo());

        ProgramProvider pp = programManager.getCurrentProgramInDomain(getLoggedInInfo(), getLoggedInInfo().getLoggedInProviderNo());

        if (pp != null) {
            tickler.setProgramId(pp.getProgramId().intValue());
        }

        if (ticklerManager.addTickler(getLoggedInInfo(), tickler)) {
            return RestResponse.successResponse(null);
        }
        rollbackFailedMutation();
        return RestResponse.errorResponse("Failed to add tickler");
    }

    private void rollbackFailedMutation() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private io.github.carlos_emr.carlos.webserv.rest.util.PatientApiPages.Page<Tickler> authorizedPage(
            CustomFilter filter, int offset, int limit, boolean comments, boolean updates) {
        return io.github.carlos_emr.carlos.webserv.rest.util.PatientApiPages.read(
                getLoggedInInfo(), securityInfoManager, "_tickler", offset, limit,
                (rawOffset, batch) -> ticklerManager.getTicklers(getLoggedInInfo(), filter, rawOffset, batch,
                        comments, updates, false, false), Tickler::getDemographicNo, Tickler::getId);
    }

    private Integer requireStoredTicklerPatient(Integer ticklerId, String right) {
        if (ticklerId == null || ticklerId <= 0) throw new jakarta.ws.rs.BadRequestException("Invalid tickler identifier");
        Integer patient = ticklerManager.getTicklerDemographicNoForUpdate(getLoggedInInfo(), ticklerId);
        if (patient == null) throw new jakarta.ws.rs.NotFoundException();
        io.github.carlos_emr.carlos.webserv.rest.util.PatientApiAccess.require(
                getLoggedInInfo(), securityInfoManager, "_tickler", right, patient);
        return patient;
    }

    private void requireFilterPatient(CustomFilter filter) {
        String value = filter.getDemographicNo();
        if (value == null || value.isBlank()) return; // Existing empty filter means discovery across patients.
        Integer patient;
        try { patient = Integer.valueOf(value.trim()); }
        catch (NumberFormatException invalid) { throw new jakarta.ws.rs.BadRequestException("Invalid patient identifier"); }
        io.github.carlos_emr.carlos.webserv.rest.util.PatientApiAccess.require(
                getLoggedInInfo(), securityInfoManager, "_tickler", "r", patient);
    }

    @GET
    @Path("/{demographicNo}/count/overdue")
    @Produces("application/json")
    public int getTicklerOverdueCount(@PathParam("demographicNo") Integer demographicNo) {
        io.github.carlos_emr.carlos.webserv.rest.util.PatientApiAccess.require(getLoggedInInfo(), securityInfoManager, "_tickler", "r", demographicNo);

        int count = ticklerManager.getActiveTicklerByDemoCount(getLoggedInInfo(), demographicNo);
        return count;
    }
}
