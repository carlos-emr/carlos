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
package io.github.carlos_emr.carlos.webserv.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.managers.BillingManager;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ServiceTypeConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRESTResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ServiceTypeTo;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.OscarProperties;

@Path("/billing")
@Consumes(MediaType.APPLICATION_JSON)
public class BillingService extends AbstractServiceImpl {

    @Autowired
    BillingManager billingManager;

    private OscarProperties oscarProperties = OscarProperties.getInstance();

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
    public GenericRESTResponse billingRegion() {
        boolean billRegionSet = true;
        String billRegion = oscarProperties.getProperty("billregion", "").trim().toUpperCase();
        if (billRegion.isEmpty()) {
            billRegionSet = false;
        }
        return new GenericRESTResponse(billRegionSet, billRegion);
    }

    @GET
    @Path("/defaultView")
    @Produces("application/json")
    public GenericRESTResponse defaultView() {
        boolean defaultViewSet = true;
        String defaultView = oscarProperties.getProperty("default_view", "").trim();
        if (defaultView.isEmpty()) {
            defaultViewSet = false;
        }
        return new GenericRESTResponse(defaultViewSet, defaultView);
    }
}
