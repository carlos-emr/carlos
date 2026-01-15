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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Hl7TextMessage;
import ca.openosp.openo.managers.LabManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.webserv.rest.conversion.Hl7TextMessageConverter;
import ca.openosp.openo.webserv.rest.to.LabResponse;
import ca.openosp.openo.webserv.rest.to.model.Hl7TextMessageTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.util.Utilities;

@Path("/labs")
@Component("labService")
@Consumes(MediaType.APPLICATION_JSON)
public class LabService extends AbstractServiceImpl {
	private static Logger logger = MiscUtils.getLogger();

    @Autowired
	private LabManager labManager;
	@Autowired
	private SecurityInfoManager securityInfoManager;

    @GET
    @Path("/hl7LabsByDemographicNo")
    @Produces("application/json")
    public LabResponse getHl7LabsByDemographicNo(@QueryParam("demographicNo") int demographicNo, @QueryParam("offset") int offset, @QueryParam("limit") int limit) {
//	public LabResponse getHl7LabsByDemographicNo() {

        LabResponse response = new LabResponse();

        List<Hl7TextMessage> hl7TextMessages = labManager.getHl7Messages(getLoggedInInfo(), demographicNo, offset, limit);

        Hl7TextMessageConverter converter = new Hl7TextMessageConverter();
        response.setMessages(converter.getAllAsTransferObjects(getLoggedInInfo(), hl7TextMessages));

        return response;
    }

	@POST
	@Path("/hl7LabUpload")
	@Produces("application/json")
	@Consumes("application/json")
	public Response uploadHl7Lab(Hl7TextMessageTo1 labT, @Context HttpServletRequest request) {
		LoggedInInfo loggedInInfo = getLoggedInInfo();

		String type = labT.getType();
		if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.WRITE, "")) {
			logger.error("Write Access Denied _lab for provider {}", loggedInInfo.getLoggedInProviderNo());
			return Response.status(Response.Status.FORBIDDEN).entity(createResponseMap(labT.getFileName(), "Failed", "Access Denied", null, type)).build();
		}

		// Validate input
        if (isInvalidHl7TextMessageTo1(labT)) { return Response.status(Response.Status.BAD_REQUEST).entity(createResponseMap(labT.getFileName(), "Failed", "Missing required fields: fileName, message, or type", null, type)).build(); }

		String filePath;
		try (InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(labT.getBase64EncodedeMessage()))) {
			filePath = Utilities.saveFile(inputStream, labT.getFileName());
		} catch (IOException e) {
			logger.error("Error occurred while saving " + labT.getFileName() + " file", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(createResponseMap(labT.getFileName(), "Failed", "File save failed due to server error", null, type)).build();
		}

		int checkFileUploadedSuccessfully;
        try (InputStream localFileInputStream = Files.newInputStream(Paths.get(filePath))) {
            checkFileUploadedSuccessfully = FileUploadCheck.addFile(Paths.get(filePath).getFileName().toString(), localFileInputStream, loggedInInfo.getLoggedInProviderNo());
        } catch (IOException e) {
            logger.error("Error occurred while processing " + labT.getFileName() + " file", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(createResponseMap(labT.getFileName(), "Failed", "Error occurred while processing the file", null, type)).build();
        }

		if (checkFileUploadedSuccessfully == FileUploadCheck.UNSUCCESSFUL_SAVE) {
			return Response.status(Response.Status.CONFLICT).entity(createResponseMap(labT.getFileName(), "Failed", "The lab already exists", null, type)).build();
		}

        MessageHandler msgHandler = HandlerClassFactory.getHandler(type);
        if ((msgHandler.parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, request.getRemoteAddr())) == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity(createResponseMap(labT.getFileName(), "Failed", "File processing failed. Invalid file", null, type)).build();
        }

		Integer lastLabNo = Optional.ofNullable(msgHandler.getLastLabNo())
									.filter(n -> n > 0)
									.orElse(null);
		if (lastLabNo != null) {
			Hl7TextMessage hl7TextMessage = labManager.getHl7Message(loggedInInfo, lastLabNo);
			Hl7TextMessageConverter converter = new Hl7TextMessageConverter();
			labT = converter.getAsTransferObject(loggedInInfo, hl7TextMessage);
			labT.setFileName(Paths.get(filePath).getFileName().toString());
		}
		return Response.ok(labT).build();
	}

	private Map<String, Object> createResponseMap(String fileName, String status, String message, Integer labNo, String type) {
		Map<String, Object> responseMap = new HashMap<>();
		if (fileName != null) responseMap.put("fileName", fileName);
		if (labNo != null) responseMap.put("labNo", labNo);
		if (type != null) responseMap.put("type", type);
		responseMap.put("status", status);
		responseMap.put("message", message);
		return responseMap;
	}

	private boolean isInvalidHl7TextMessageTo1(Hl7TextMessageTo1 labT) {
		return labT == null ||
			   StringUtils.isEmpty(labT.getFileName()) ||
			   StringUtils.isEmpty(labT.getBase64EncodedeMessage()) ||
			   StringUtils.isEmpty(labT.getType());
	}

}
