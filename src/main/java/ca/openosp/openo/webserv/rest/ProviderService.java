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
 * 
 * Migrated from CXF OAuth2 to ScribeJava OAuth1 implementation.
 */
package ca.openosp.openo.webserv.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import ca.openosp.openo.webserv.rest.to.OscarSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.OscarLogManager;
import ca.openosp.openo.managers.ProviderManager2;
import ca.openosp.openo.managers.model.ProviderSettings;
import ca.openosp.openo.utility.JsDateSerializer;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.web.PatientListApptBean;
import ca.openosp.openo.web.PatientListApptItemBean;
import ca.openosp.openo.webserv.rest.conversion.ProviderConverter;
import ca.openosp.openo.webserv.rest.to.AbstractSearchResponse;
import ca.openosp.openo.webserv.rest.to.GenericRESTResponse;
import ca.openosp.openo.webserv.rest.to.model.ProviderTo1;
import ca.openosp.openo.webserv.transfer_objects.ProviderTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * REST service for provider-related operations using OAuth 1.0a authentication.
 *
 * This service provides endpoints for managing providers, retrieving provider information,
 * and handling provider settings. It uses ScribeJava OAuth1 for authentication.
 */
@Component("ProviderService")
@Path("/providerService/")
@Produces("application/xml")
@Consumes(MediaType.APPLICATION_JSON)
public class ProviderService extends AbstractServiceImpl {

    private static final Logger logger = MiscUtils.getLogger();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }

    @Autowired
    ProviderDao providerDao;

    @Autowired
    ProviderManager2 providerManager;

    @Autowired
    OscarLogManager oscarLogManager;

    @Autowired
    DemographicManager demographicManager;



    public ProviderService() {
    }

    /**
     * Retrieves all active providers.
     * 
     * @return OscarSearchResponse containing list of providers
     * @deprecated Use getProvidersAsJSON instead
     */
    @GET
    @Path("/providers")
    @Deprecated
    public OscarSearchResponse<ProviderTransfer> getProviders() {
        try {
            logger.debug("Retrieving active providers");

            OscarSearchResponse<ProviderTransfer> lst = new
                OscarSearchResponse<ProviderTransfer>();

            for (Provider p : providerDao.getActiveProviders()) {
                lst.getContent().add(ProviderTransfer.toTransfer(p));
            }
            lst.setTimestamp(new Date());
            lst.setTotal(lst.getContent().size());

            logger.info("Successfully retrieved {} providers", lst.getTotal());
            return lst;
            
        } catch (Exception e) {
            logger.error("Error retrieving providers: {}", e.getMessage(), e);
            throw new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves all active providers as JSON.
     * 
     * @return AbstractSearchResponse containing list of providers in JSON format
     */
    @GET
    @Path("/providers_json")
    @Produces("application/json")
    public AbstractSearchResponse<ProviderTo1> getProvidersAsJSON() {
        try {
            logger.debug("Retrieving active providers as JSON");


            List<ProviderTo1> providers = new ProviderConverter().getAllAsTransferObjects(getLoggedInInfo(), providerDao.getActiveProviders());

            AbstractSearchResponse<ProviderTo1> response = new AbstractSearchResponse<ProviderTo1>();
            response.setContent(providers);
            response.setTimestamp(new Date());
            response.setTotal(response.getContent().size());

            logger.info("Successfully retrieved {} providers as JSON", response.getTotal());
            return response;
            
        } catch (Exception e) {
            logger.error("Error retrieving providers as JSON: {}", e.getMessage(), e);
            throw new javax.ws.rs.WebApplicationException(javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

        /**
         * Retrieves a specific provider by ID.
         *
         * @param id The provider ID
         * @return ProviderTransfer object
         */
        @GET
        @Path("/provider/{id}")
        @Produces({"application/xml", "application/json"})
        public ProviderTransfer getProvider(@PathParam("id") String id) {
            logger.debug("Retrieving provider {}", id);

            Provider provider = providerDao.getProvider(id);
            if (provider == null) {
                logger.warn("Provider not found: {}", id);
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }

            logger.info("Successfully retrieved provider: {}", id);
            return ProviderTransfer.toTransfer(provider);
        }

    /**
     * Retrieves the currently logged-in provider.
     *
     * @return JSON representation of the logged-in provider, or 404 if none
     */
    @GET
    @Path("/provider/me")
    @Produces("application/json")
    public Response getLoggedInProvider() {
        try {
            logger.debug("Retrieving logged-in provider");
            Provider provider = getLoggedInInfo().getLoggedInProvider();

            if (provider == null) {
                // build a JSON error payload
                ObjectNode error = objectMapper.createObjectNode();
                error.put("error", "Provider not found");
                return Response.status(Status.NOT_FOUND)
                               .entity(error.toString())
                               .type("application/json")
                               .build();
            }

            // serialize the provider to JSON
            String body = objectMapper.valueToTree(provider).toString();

            logger.info("Successfully retrieved logged-in provider: {}", provider.getProviderNo());
            return Response.ok(body, "application/json").build();
        }
        catch (WebApplicationException e) {
            throw e;
        }
        catch (Exception e) {
            logger.error("Error retrieving logged-in provider: {}", e.getMessage(), e);
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * Retrieves a provider as JSON by ID.
     *
     * @param id The provider ID
     * @return JSON representation of the provider
     */
    @GET
    @Path("/providerjson/{id}")
    @Produces("application/json")
    public String getProviderAsJSON(@PathParam("id") String id) {
        logger.debug("Retrieving provider {} as JSON", id);

        Provider provider = providerDao.getProvider(id);
        if (provider == null) {
            logger.warn("Provider not found: {}", id);
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        logger.info("Successfully retrieved provider {} as JSON", id);
        return objectMapper.valueToTree(provider).toString();
    }


    @GET
    @Path("/providers/bad")
    public Response getBadRequest() {
        return Response.status(Status.BAD_REQUEST).build();
    }

    /**
     * Searches for providers based on criteria.
     *
     * @param json            Search criteria
     * @param startIndex      Starting index for pagination
     * @param itemsToReturn   Number of items to return
     * @return AbstractSearchResponse containing search results
     */
    @POST
    @Path("/providers/search")
    @Produces("application/json")
    @Consumes("application/json")
    public AbstractSearchResponse<ProviderTo1> search(
        ObjectNode json,
        @QueryParam("startIndex") Integer startIndex,
        @QueryParam("itemsToReturn") Integer itemsToReturn
    ) {
        try {
            logger.debug("Searching providers");

            // 1) Validate and coerce 'active'
            Boolean active = null;
            if (json.has("active")) {
                JsonNode activeNode = json.get("active");
                if (activeNode.isBoolean()) {
                    active = activeNode.asBoolean();
                } else if (activeNode.isTextual()) {
                    String s = activeNode.asText().trim().toLowerCase();
                    if ("true".equals(s))      active = true;
                    else if ("false".equals(s)) active = false;
                    else throw new WebApplicationException(
                        "Invalid 'active' value", Response.Status.BAD_REQUEST);
                } else {
                    throw new WebApplicationException(
                        "'active' must be a boolean", Response.Status.BAD_REQUEST);
                }
            }

            // 2) Sanitize 'searchTerm'
            String term = null;
            if (json.has("searchTerm")) {
                term = json.has("searchTerm") ? json.get("searchTerm").asText() : null;
                if (term != null) {
                    term = term.replaceAll("[^\\w\\s\\-\\.]", "").trim();
                    if (term.length() > 100) {
                        throw new WebApplicationException(
                            "Search term too long", Response.Status.BAD_REQUEST);
                    }
                }
            }

            // 3) Pagination defaults
            int start = (startIndex == null ? 0 : startIndex);
            int count = (itemsToReturn == null ? 5000 : itemsToReturn);

            // 4) Delegate to manager/DAO using sanitized inputs
            List<Provider> results = providerManager.search(
                getLoggedInInfo(), term, active, start, count);

            // build response...
            AbstractSearchResponse<ProviderTo1> resp = new AbstractSearchResponse<>();
            resp.setContent(
                new ProviderConverter().getAllAsTransferObjects(getLoggedInInfo(), results)
            );
            resp.setTotal(resp.getContent().size());
            return resp;

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error searching providers", e);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }


    @GET
    @Path("/getRecentDemographicsViewed")
    @Produces("application/json")
    public PatientListApptBean getRecentDemographicsViewed(@QueryParam("startIndex") Integer startIndex, @QueryParam("itemsToReturn") Integer itemsToReturn) {
        List<Object[]> results = oscarLogManager.getRecentDemographicsViewedByProvider(getLoggedInInfo(), getLoggedInInfo().getLoggedInProviderNo(), startIndex, itemsToReturn);

        return convertRecentViewedDemographicsResults(results);
    }

    @GET
    @Path("/getRecentDemographicsViewedAfterDateIncluded")
    @Produces("application/json")
    public PatientListApptBean getRecentDemographicsViewed(@QueryParam("startIndex") Integer startIndex, @QueryParam("itemsToReturn") Integer itemsToReturn, @QueryParam("date") String dateStr) {
        Date date = new Date(Long.parseLong(dateStr));
        List<Object[]> results = oscarLogManager.getRecentDemographicsViewedByProviderAfterDateIncluded(getLoggedInInfo(), getLoggedInInfo().getLoggedInProviderNo(), date, startIndex, itemsToReturn);

        return convertRecentViewedDemographicsResults(results);
    }

    private PatientListApptBean convertRecentViewedDemographicsResults(List<Object[]> results) {
        PatientListApptBean response = new PatientListApptBean();

        for (Object[] r : results) {
            Demographic d = demographicManager.getDemographic(getLoggedInInfo(), (Integer) r[0]);
            if (d != null) {
                PatientListApptItemBean item = new PatientListApptItemBean();
                item.setDemographicNo((Integer) r[0]);
                item.setDate((Date) r[1]);
                item.setName(d.getFormattedName());
                response.getPatients().add(item);
            }
        }

        return response;
    }

    @GET
    @Path("/getActiveTeams")
    @Produces("application/json")
    public AbstractSearchResponse<String> getActiveTeams() {
        List<String> teams = providerManager.getActiveTeams(getLoggedInInfo());

        AbstractSearchResponse<String> response = new AbstractSearchResponse<String>();

        response.setContent(teams);
        response.setTotal(response.getContent().size());
        return response;
    }

    @GET
    @Path("/settings/get")
    @Produces("application/json")
    public AbstractSearchResponse<ProviderSettings> getProviderSettings() {
        AbstractSearchResponse<ProviderSettings> response = new AbstractSearchResponse<ProviderSettings>();

        ProviderSettings settings = providerManager.getProviderSettings(getLoggedInInfo(), getLoggedInInfo().getLoggedInProviderNo());
        List<ProviderSettings> content = new ArrayList<ProviderSettings>();
        content.add(settings);
        response.setContent(content);
        response.setTotal(1);
        return response;
    }

    @POST
    @Path("/settings/{providerNo}/save")
    @Produces("application/json")
    @Consumes("application/json")
    public GenericRESTResponse saveProviderSettings(ProviderSettings json, @PathParam("providerNo") String providerNo) {
        GenericRESTResponse response = new GenericRESTResponse();

        MiscUtils.getLogger().warn(json.toString());

        providerManager.updateProviderSettings(getLoggedInInfo(), providerNo, json);
        return response;
    }

    @GET
    @Path("/suggestProviderNo")
    @Produces("application/json")
    public GenericRESTResponse suggestProviderNo() {

        List<Provider> providers = providerManager.getProviders(getLoggedInInfo(), null);
        List<Integer> providerList = new ArrayList<Integer>();
        for (Provider h : providers) {
            try {
                String pn = h.getProviderNo();
                providerList.add(Integer.valueOf(pn));
            } catch (
                    Exception e) {/*empty*/} /*No need to do anything. Just want to avoid a NumberFormatException from provider numbers with alphanumeric Characters*/
        }

        String suggestProviderNo = "";
        for (Integer i = 1; i < 1000000; i++) {
            if (!providerList.contains(i)) {
                suggestProviderNo = i.toString();
                break;
            }
        }


        return new GenericRESTResponse(true, suggestProviderNo);
    }

}
