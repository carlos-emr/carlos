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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidationFactory;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidationResult;
import io.github.carlos_emr.carlos.integration.mchcv.HCValidator;
import io.github.carlos_emr.carlos.integration.mchcv.OnlineHCValidator;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PatientDetailStatusTo1;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.CarlosProperties;


@Path("/patientDetailStatusService")
@Produces({MediaType.APPLICATION_JSON})
@Consumes(MediaType.APPLICATION_JSON)
public class PatientDetailStatusService extends AbstractServiceImpl {
    @Autowired
    private DemographicManager demographicManager;

    private CarlosProperties oscarProperties = CarlosProperties.getInstance();
    private Logger logger = MiscUtils.getLogger();


    @GET
    @Path("/getStatus")
    public PatientDetailStatusTo1 getStatus(@QueryParam("demographicNo") Integer demographicNo) {
        PatientDetailStatusTo1 status = new PatientDetailStatusTo1();

        //from carlos.properties
        status.setConformanceFeaturesEnabled(oscarProperties.isPropertyActive("ENABLE_CONFORMANCE_ONLY_FEATURES"));
        status.setWorkflowEnhance(oscarProperties.isPropertyActive("workflow_enhance"));
        status.setBillregion(oscarProperties.getProperty("billregion", ""));
        status.setDefaultView(oscarProperties.getProperty("default_view", ""));
        status.setHospitalView(oscarProperties.getProperty("hospital_view", status.getDefaultView()));
        status.setShowPrimaryCarePhysicianCheck(oscarProperties.isPropertyActive("showPrimaryCarePhysicianCheck"));
        status.setShowEmploymentStatus(oscarProperties.isPropertyActive("showEmploymentStatus"));

        return status;
    }

    @GET
    @Path("/validateHC")
    public HCValidationResult validateHC(@QueryParam("hin") String healthCardNo, @QueryParam("ver") String versionCode) {
        HCValidator validator = HCValidationFactory.getHCValidator();
        HCValidationResult result = null;

        if (validator.getClass().equals(OnlineHCValidator.class)) {
            HCValidator simpleValidator = HCValidationFactory.getSimpleValidator();
            result = simpleValidator.validate(healthCardNo, versionCode);

            if (result.isValid()) result = null;
        }

        if (result == null) {
            try {
                result = validator.validate(healthCardNo, versionCode);
            } catch (Exception ex) {
                logger.error("Error doing HCValidation", ex);
            }
        }

        if (result != null && result.getResponseDescription() == null) {
            if (result.isValid()) result.setResponseDescription("Valid Health Card Number");
            else result.setResponseDescription("Invalid Health Card Number");
        }
        return result;
    }

    @GET
    @Path("/isUniqueHC")
    public RestResponse<String> isUniqueHC(@QueryParam("hin") String healthCardNo, @QueryParam("demographicNo") Integer demographicNo) {
        if (healthCardNo != null && !healthCardNo.trim().isEmpty() && demographicNo != null) {
            List<Demographic> demos = demographicManager.searchByHealthCard(getLoggedInInfo(), healthCardNo);
            if (demos != null) {
                if (demos.size() > 1 || (demos.size() == 1 && !demos.get(0).getDemographicNo().equals(demographicNo))) {
                    return RestResponse.errorResponse("Health card number is not unique");
                }
            }
        }
        return RestResponse.successResponse(null);
    }
}
