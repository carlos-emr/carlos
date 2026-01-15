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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.managers.MeasurementManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.webserv.rest.to.MeasurementResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RESTful web service for measurement data operations in OpenO EMR.
 * This service provides secure, OAuth-authenticated access to measurement data
 * for external integrations, mobile applications, and API consumers.
 * 
 * <p>The service follows REST conventions and provides JSON-based endpoints for:</p>
 * <ul>
 *   <li><strong>Data retrieval</strong>: Getting measurements by type, patient, and date ranges</li>
 *   <li><strong>Security enforcement</strong>: OAuth 1.0a authentication and authorization</li>
 *   <li><strong>Data transformation</strong>: Converting internal models to API-friendly formats</li>
 *   <li><strong>Type filtering</strong>: Selective measurement retrieval by clinical type</li>
 * </ul>
 * 
 * <p>Security model:</p>
 * <ul>
 *   <li>All endpoints require OAuth 1.0a authentication</li>
 *   <li>Users must have "_measurement" read privileges</li>
 *   <li>Patient demographic access is validated per request</li>
 *   <li>Audit logging tracks all API access</li>
 * </ul>
 * 
 * <p>Data formats:</p>
 * <ul>
 *   <li>Input: JSON with measurement type arrays</li>
 *   <li>Output: {@link MeasurementResponse} objects with measurement collections</li>
 *   <li>All responses include metadata and status information</li>
 * </ul>
 * 
 * <p>Example API usage:</p>
 * <pre>{@code
 * POST /ws/rs/measurements/{demographicNo}
 * Content-Type: application/json
 * 
 * {
 *   "types": ["BP", "WT", "HT"]
 * }
 * }</pre>
 * 
 * @since 2006
 * @see Measurement
 * @see MeasurementResponse
 * @see MeasurementManager
 * @see AbstractServiceImpl
 */
@Path("/measurements")
@Component("measurementService")
@Consumes(MediaType.APPLICATION_JSON)
public class MeasurementService extends AbstractServiceImpl {
    /** Security manager for OAuth authentication and authorization. */
    @Autowired
    private SecurityInfoManager securityInfoManager;
    
    /** Business logic manager for measurement operations. */
    @Autowired
    private MeasurementManager measurementManager;

    /**
     * Retrieves measurements of specified types for a patient via REST API.
     * This endpoint accepts a JSON payload containing measurement type codes and returns
     * matching measurement data for the specified patient.
     * 
     * <p>Request format:</p>
     * <pre>{@code
     * {
     *   "types": ["BP", "WT", "HT", "GLU"]
     * }
     * }</pre>
     * 
     * <p>Security requirements:</p>
     * <ul>
     *   <li>Valid OAuth 1.0a authentication token</li>
     *   <li>"_measurement" read privilege</li>
     *   <li>Access to the specified patient's demographic data</li>
     * </ul>
     * 
     * <p>Response includes all measurements of the requested types for the patient,
     * transformed into API-friendly format with metadata.</p>
     * 
     * @param json JSONObject containing array of measurement type codes to retrieve
     * @param demoId Integer the patient's demographic ID from the URL path
     * @return MeasurementResponse containing matching measurements and metadata
     * @throws SecurityException if user lacks required measurement read privileges
     * 
     * @see MeasurementResponse
     * @see MeasurementManager#getMeasurementByType(ca.openosp.openo.utility.LoggedInInfo, Integer, List)
     */
    @POST
    @Path("/{demographicNo}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public MeasurementResponse getMeasurements(ObjectNode json, @PathParam("demographicNo") Integer demoId) {
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_measurement", "r", null)) {
            throw new SecurityException("Access Denied: Missing required sec object (_measurement)");
        }
        MeasurementResponse response = new MeasurementResponse();
        ArrayNode jsonArray = (ArrayNode) json.get("types");
        List<String> typesList = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            typesList.add(jsonArray.get(i).asText());
        }
        String[] types = typesList.toArray(new String[0]);
        if (types.length < 1) {
            return response;
        }

        List<Measurement> measurements = measurementManager.getMeasurementByType(getLoggedInInfo(), demoId, new ArrayList<String>(Arrays.asList(types)));
        response.addMeasurements(measurements);
        return response;
    }
}
