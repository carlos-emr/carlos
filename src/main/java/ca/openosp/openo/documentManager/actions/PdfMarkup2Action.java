//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2026.
 */

package ca.openosp.openo.documentManager.actions;

import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.managers.PdfMarkupManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.providers.data.ProSignatureData;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Struts2 action for PDF markup and annotation operations.
 * Provides endpoints for saving annotated PDF copies and retrieving
 * provider signatures for insertion into documents.
 *
 * <p>Follows the 2Action pattern used throughout the OpenO EMR codebase.</p>
 *
 * <h3>API Endpoints:</h3>
 * <ul>
 *   <li>POST pdfMarkup.do?method=saveAnnotatedCopy - Flatten annotations and save as new document</li>
 *   <li>GET pdfMarkup.do?method=getSignature - Get provider's signature image (base64)</li>
 * </ul>
 *
 * @since 2026-01-23
 */
public class PdfMarkup2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private PdfMarkupManager pdfMarkupManager = SpringUtils.getBean(PdfMarkupManager.class);
    private ProSignatureData proSignatureData = new ProSignatureData();

    /**
     * Main execute method that routes to appropriate handler based on 'method' parameter.
     *
     * @return String the Struts result name
     * @throws Exception if processing fails
     */
    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("saveAnnotatedCopy".equals(method)) {
            return saveAnnotatedCopy();
        } else if ("getSignature".equals(method)) {
            return getSignature();
        }
        return SUCCESS;
    }

    /**
     * Saves an annotated copy of a PDF document.
     * Expects POST parameters: originalDocumentNo, demographicNo, annotationsJson
     *
     * @return null (response written directly)
     * @throws Exception if processing fails
     */
    private String saveAnnotatedCopy() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Security check
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required security object _edoc");
        }

        // Read URL-encoded parameters
        String originalDocNo = request.getParameter("originalDocumentNo");
        String demographicNo = request.getParameter("demographicNo");
        String annotationsJson = request.getParameter("annotationsJson");

        // Validate inputs
        if (originalDocNo == null || demographicNo == null || annotationsJson == null) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "Missing required parameters");
            writeJsonResponse(error);
            return null;
        }

        try {
            Document saved = pdfMarkupManager.createAnnotatedCopy(
                loggedInInfo,
                Integer.parseInt(originalDocNo),
                Integer.parseInt(demographicNo),
                annotationsJson
            );

            ObjectNode json = objectMapper.createObjectNode();
            json.put("success", true);
            json.put("newDocumentNo", saved.getDocumentNo());
            writeJsonResponse(json);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("Invalid document or demographic number", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "Invalid document or demographic number");
            writeJsonResponse(error);
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Security error saving annotated copy", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "Access denied: " + e.getMessage());
            writeJsonResponse(error);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error saving annotated copy", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "An error occurred while saving the annotated document");
            writeJsonResponse(error);
        }
        return null;
    }

    /**
     * Gets the current provider's signature image.
     * Returns JSON with hasSignature boolean and signature base64 string.
     *
     * @return null (response written directly)
     * @throws Exception if processing fails
     */
    private String getSignature() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Security check - user must have read access to documents
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required security object _edoc");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String signature = proSignatureData.getSignature(providerNo);

        ObjectNode json = objectMapper.createObjectNode();
        json.put("hasSignature", signature != null && !signature.isEmpty());
        json.put("signature", signature != null ? signature : "");
        writeJsonResponse(json);
        return null;
    }

    /**
     * Writes a JSON response to the HTTP response output stream.
     *
     * @param json ObjectNode the JSON object to write
     * @throws IOException if writing fails
     */
    private void writeJsonResponse(ObjectNode json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
        response.flushBuffer();
    }
}
