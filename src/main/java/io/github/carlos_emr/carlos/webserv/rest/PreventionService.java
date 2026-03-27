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

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PreventionConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;


@Path("/preventions/")
@Component("preventionService")
@Consumes(MediaType.APPLICATION_JSON)
public class PreventionService extends AbstractServiceImpl {

    @Autowired
    private PreventionManager preventionManager;

    @GET
    @Path("/active")
    @Produces(MediaType.APPLICATION_JSON)
    public PreventionResponse getCurrentPreventions(@QueryParam("demographicNo") Integer demographicNo) {
        List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(getLoggedInInfo(), demographicNo);

        List<PreventionTo1> preventionsT = new PreventionConverter().getAllAsTransferObjects(getLoggedInInfo(), preventions);

        PreventionResponse response = new PreventionResponse();
        response.setPreventions(preventionsT);

        return response;
    }

    @GET
    @Path("/immunizations/{demographicNo}")
    @Produces({MediaType.APPLICATION_JSON})
    public PreventionResponse getImmunizations(@PathParam("demographicNo") Integer demographicNo) {
        List<Prevention> immunizations = preventionManager.getImmunizationsByDemographic(getLoggedInInfo(), demographicNo);
        List<PreventionTo1> preventionsT = new PreventionConverter().getAllAsTransferObjects(getLoggedInInfo(), immunizations);
        PreventionResponse response = new PreventionResponse();
        response.setPreventions(preventionsT);
        return response;
    }

}
