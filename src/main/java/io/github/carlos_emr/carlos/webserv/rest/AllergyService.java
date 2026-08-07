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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AllergyConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AllergyResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AllergyTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Path("/allergies")
@Component("allergyService")
@Consumes(MediaType.APPLICATION_JSON)
public class AllergyService extends AbstractServiceImpl {

    @Autowired
    private AllergyManager allergyManager;

    @GET
    @Path("/active")
    @Produces("application/json")
    public AllergyResponse getCurrentAllergies(@QueryParam("demographicNo") Integer demographicNo) {
        List<Allergy> allergies = allergyManager.getActiveAllergies(getLoggedInInfo(), demographicNo);

        List<AllergyTo1> allergiesT = new AllergyConverter().getAllAsTransferObjects(getLoggedInInfo(), allergies);

        AllergyResponse response = new AllergyResponse();
        response.setAllergies(allergiesT);

        return response;
    }
}
