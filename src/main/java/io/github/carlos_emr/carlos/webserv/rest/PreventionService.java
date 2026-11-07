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
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;

import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PreventionConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionTo1;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import jakarta.ws.rs.core.Response;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.core.MediaType;


/**
 * Prevention reads with an additive precision protocol. Legacy Date-only operations return
 * HTTP 409 for affected history; /precision operations retain each record with native date info.
 */
@Path("/preventions/")
@Component("preventionService")
@Consumes(MediaType.APPLICATION_JSON)
public class PreventionService extends AbstractServiceImpl {

    private final PreventionManager preventionManager;
    private final PreventionDateService dateService;
    private final io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections;

    public PreventionService(PreventionManager preventionManager, PreventionDateService dateService,
            io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService projections) {
        this.preventionManager = preventionManager;
        this.dateService = dateService;
        this.projections = projections;
    }

    @Autowired
    private SecurityInfoManager securityInfoManager;

    /**
     * Returns the active prevention records for a patient.
     *
     * @param demographicNo the patient demographic identifier from the request
     * @return a {@link PreventionResponse} JSON payload containing the patient's active
     * prevention records
     * @throws WebApplicationException with HTTP 400 when {@code demographicNo} is missing or
     * non-positive
     * @throws WebApplicationException with HTTP 403 when the current user lacks
     * {@code _prevention} read access
     * @since 2026-06-24
     */
    @GET
    @Path("/active")
    @Produces(MediaType.APPLICATION_JSON)
    public PreventionResponse getCurrentPreventions(@QueryParam("demographicNo") Integer demographicNo) {
        requirePreventionReadPrivilege(demographicNo);
        List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(getLoggedInInfo(), demographicNo);

        projections.requireRestSupported(demographicNo, java.util.Set.of("Immunizations"));
        return response(preventions, false);
    }

    /**
     * Returns the immunization records for a patient.
     *
     * <p>A request that omits the required {@code demographicNo} path segment does not match
     * this route and receives HTTP 404 before this method is invoked. A non-positive numeric
     * identifier matches the route but receives HTTP 400.</p>
     *
     * @param demographicNo the patient demographic identifier from the request path
     * @return a {@link PreventionResponse} JSON payload containing the patient's immunization
     * records
     * @throws WebApplicationException with HTTP 400 when {@code demographicNo} is non-positive
     * @throws WebApplicationException with HTTP 403 when the current user lacks
     * {@code _prevention} read access
     * @since 2026-06-24
     */
    @GET
    @Path("/immunizations/{demographicNo}")
    @Produces({MediaType.APPLICATION_JSON})
    public PreventionResponse getImmunizations(@PathParam("demographicNo") Integer demographicNo) {
        requirePreventionReadPrivilege(demographicNo);
        List<Prevention> immunizations = preventionManager.getImmunizationsByDemographic(getLoggedInInfo(), demographicNo);
        projections.requireRestSupported(demographicNo, java.util.Set.of("Immunizations"));
        return response(immunizations, false);
    }

    @GET
    @Path("/active/precision")
    @Produces(MediaType.APPLICATION_JSON)
    public PreventionResponse getCurrentPreventionsWithPrecision(@QueryParam("demographicNo") Integer demographicNo) {
        List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(getLoggedInInfo(), demographicNo);
        projections.requireRestSupported(demographicNo, java.util.Set.of("Immunizations"));
        return response(preventions, true);
    }

    @GET
    @Path("/immunizations/{demographicNo}/precision")
    @Produces(MediaType.APPLICATION_JSON)
    public PreventionResponse getImmunizationsWithPrecision(@PathParam("demographicNo") Integer demographicNo) {
        List<Prevention> preventions = preventionManager.getImmunizationsByDemographic(getLoggedInInfo(), demographicNo);
        projections.requireRestSupported(demographicNo, java.util.Set.of("Immunizations"));
        return response(preventions, true);
    }

    private PreventionResponse response(List<Prevention> preventions, boolean precisionAware) {
        List<PreventionTo1> preventionsT;
        try {
            preventionsT = new PreventionConverter(dateService, precisionAware)
                    .getAllAsTransferObjects(getLoggedInInfo(), preventions);
        } catch (PreventionDatePrecisionException unsafe) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .entity(unsafe.getMessage() + "; append /precision to this operation").build());
        }
        PreventionResponse response = new PreventionResponse();
        response.setPreventions(preventionsT);
        return response;
    }

    /**
     * Enforces patient-level read access to prevention data for the given demographic.
     *
     * <p>Guards the prevention endpoints so a patient's preventive measures and
     * immunization history cannot be read by supplying an arbitrary {@code demographicNo}.
     *
     * @param demographicNo the demographic whose prevention data is being requested.
     * @throws WebApplicationException with HTTP 400 when {@code demographicNo} is missing or
     * non-positive, or HTTP 403 when the current user lacks {@code _prevention} read access
     */
    private void requirePreventionReadPrivilege(Integer demographicNo) {
        if (demographicNo == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("demographicNo is required").build());
        }
        if (demographicNo <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).entity("demographicNo must be positive").build());
        }
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_prevention", "r", demographicNo)) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
        }
    }

}
