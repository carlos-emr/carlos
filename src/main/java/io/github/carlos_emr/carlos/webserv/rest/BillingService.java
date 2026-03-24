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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.managers.BillingManager;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ServiceTypeConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ServiceTypeTo;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.CarlosProperties;

@Path("/billing")
@Consumes(MediaType.APPLICATION_JSON)
public class BillingService extends AbstractServiceImpl {

    @Autowired
    BillingManager billingManager;

    private CarlosProperties oscarProperties = CarlosProperties.getInstance();

    @GET
    @Path("/uniqueServiceTypes")
    @Produces("application/json")
    public AbstractSearchResponse<ServiceTypeTo> getUniqueServiceTypes(@QueryParam("type") String type) {
        AbstractSearchResponse<ServiceTypeTo> response = new AbstractSearchResponse<ServiceTypeTo>();
        ServiceTypeConverter converter = new ServiceTypeConverter();
        if (type == null) {
            response.setContent(converter.getAllAsTransferObjects(getLoggedInInfo(), billingManager.getUniqueServiceTypes(getLoggedInInfo())));
        } else {
            response.setContent(converter.getAllAsTransferObjects(getLoggedInInfo(), billingManager.getUniqueServiceTypes(getLoggedInInfo(), type)));
        }
        response.setTotal(response.getContent().size());
        return response;

    }

    @GET
    @Path("/billingRegion")
    @Produces("application/json")
    public RestResponse<String> billingRegion() {
        String billRegion = oscarProperties.getProperty("billregion", "").trim().toUpperCase();
        if (billRegion.isEmpty()) {
            return RestResponse.errorResponse("Billing region not configured");
        }
        return RestResponse.successResponse(billRegion);
    }

    @GET
    @Path("/defaultView")
    @Produces("application/json")
    public RestResponse<String> defaultView() {
        String defaultView = oscarProperties.getProperty("default_view", "").trim();
        if (defaultView.isEmpty()) {
            return RestResponse.errorResponse("Default view not configured");
        }
        return RestResponse.successResponse(defaultView);
    }
}
