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

import ca.openosp.openo.utility.MiscUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.managers.DocumentManager;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.webserv.rest.conversion.DocumentConverter;
import ca.openosp.openo.webserv.rest.to.model.DocumentTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Path("/document")
@Component("documentService")
@Consumes(MediaType.APPLICATION_JSON)
public class DocumentService extends AbstractServiceImpl {
    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    private DocumentManager documentManager;
    @Autowired
    private ProgramManager2 programManager2;
    @Autowired
    private SecurityInfoManager securityInfoManager;

    @POST
    @Path("/saveDocumentToDemographic")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveDocumentToDemographic(DocumentTo1 documentT) {
        Response response;

        if (StringUtils.isNotEmpty(documentT.getFileName()) && documentT.getFileContents().length > 0 && documentT.getDemographicNo() != null) {
            try {
                DocumentConverter documentConverter = new DocumentConverter();
                LoggedInInfo loggedInInfo = getLoggedInInfo();
                if (StringUtils.isEmpty(documentT.getSource())) {
                    documentT.setSource("REST API");
                }
                Document document = documentConverter.getAsDomainObject(loggedInInfo, documentT);
                document = documentManager.createDocument(loggedInInfo, document, documentT.getDemographicNo(), documentT.getProviderNo(), documentT.getFileContents());
                response = Response.ok(documentConverter.getAsTransferObject(loggedInInfo, document)).build();
            } catch (IOException e) {
                response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("The document could not be saved.").build();
            }
        } else {
            response = Response.status(Response.Status.BAD_REQUEST).entity("The request body must contain a title, encoded documentData, a fileType (png, jpg, pdf, etc.), and a demographicNo").build();
        }

        return response;
    }

    /*
     * The existing front-end GUI does not enforce any file upload limits.
     * To maintain consistency, no explicit limit has been applied to this REST endpoint either.
     * However, global system constraints (such as server configurations, memory limits,
     * or API rate-limiting) ensure protection against excessive resource usage.
     */
    @POST
    @Path("/uploadPendingDocuments")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadPendingDocuments(DocumentTo1 documentTo1, @Context HttpServletRequest request) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();

        // Validate access
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.WRITE, "")) {
            logger.error("Write Access Denied _edoc for provider {}", loggedInInfo.getLoggedInProviderNo());
            return Response.status(Response.Status.FORBIDDEN).entity(createResponseMap(documentTo1.getFileName(), "Failed", "Access Denied")).build();
        }

        // Validate input
        if (isInvalidDocument(documentTo1)) { return Response.status(Response.Status.BAD_REQUEST).entity(createResponseMap(documentTo1.getFileName(), "Failed", "Missing required fields: fileName, fileContent, contentType, or queue")).build(); }
        if (documentTo1.getQueue() < 1) { return Response.status(Response.Status.BAD_REQUEST).entity(createResponseMap(documentTo1.getFileName(), "Failed", "Invalid queue: must be 1 or greater")).build(); }

        if (StringUtils.isEmpty(documentTo1.getSource())) { documentTo1.setSource("REST API"); }

        DocumentConverter documentConverter = new DocumentConverter();
        Document document = documentConverter.getAsDomainObject(loggedInInfo, documentTo1);

        // Set program ID if available
        ProgramProvider programProvider = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        if (programProvider != null && programProvider.getProgramId() != null) { document.setProgramId(programProvider.getProgramId().intValue()); }

        try {
            document = documentManager.createDocument(loggedInInfo, document, documentTo1.getDemographicNo(), documentTo1.getProviderNo(), documentTo1.getFileContents());
        } catch (IOException e) {
            logger.error("Document could not be saved: {}", documentTo1.getFileName(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(createResponseMap(documentTo1.getFileName(), "Failed", "Internal error: " + e.getMessage())).build();
        }

        Integer queueId = documentManager.addDocumentToQueue(loggedInInfo, document.getDocumentNo(), documentTo1.getQueue());

        documentTo1 = documentConverter.getAsTransferObject(loggedInInfo, document);
        if (queueId != null) { documentTo1.setQueue(queueId); }

        return Response.ok(documentTo1).build();
    }

    private boolean isInvalidDocument(DocumentTo1 doc) {
        if (doc == null ||
            doc.getFileName() == null || doc.getFileName().isEmpty() ||
            doc.getFileContents() == null || doc.getFileContents().length == 0 ||
            doc.getContentType() == null || doc.getContentType().isEmpty() ||
            doc.getQueue() == null) {
            return true;
        }

        String providerNo = doc.getProviderNo();
        if (StringUtils.isEmpty(providerNo)) {
            doc.setProviderNo("0");
        } else {
            // Keep only alphanumeric characters (A–Z, a–z, 0–9)
            providerNo = providerNo.replaceAll("[^A-Za-z0-9]", "");
            doc.setProviderNo(providerNo.isEmpty() ? "0" : providerNo);
        }

        return false;
    }

    private Map<String, String> createResponseMap(String fileName, String status, String message) {
        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("status", status);
        responseMap.put("message", message);
        if (fileName != null) {
            responseMap.put("fileName", fileName);
        }
        return responseMap;
    }
}
