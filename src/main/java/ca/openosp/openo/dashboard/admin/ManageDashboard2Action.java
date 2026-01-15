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

package ca.openosp.openo.dashboard.admin;

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.dom4j.io.SAXValidator;
import org.dom4j.util.XMLErrorHandler;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import ca.openosp.openo.commn.model.Dashboard;
import ca.openosp.openo.commn.model.IndicatorTemplate;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.DashboardManager.ObjectName;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageDashboard2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        String method = request.getParameter("method");
        if ("importTemplate".equals(method)) {
            return importTemplate();
        } else if ("exportTemplate".equals(method)) {
            return exportTemplate();
        } else if ("assignDashboard".equals(method)) {
            return assignDashboard();
        } else if ("saveDashboard".equals(method)) {
            return saveDashboard();
        } else if ("toggleActive".equals(method)) {
            return toggleActive();
        }

        setRequest(loggedInInfo, request);

        return SUCCESS;
    }

    public String importTemplate() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String message = "";

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        byte[] filebytes = null;
        ObjectNode json = null;

        if (indicatorTemplateFile != null) {
            try {
                filebytes = Files.readAllBytes(indicatorTemplateFile.toPath());
            } catch (Exception e) {
                json = objectMapper.createObjectNode();
                json.put("status", "error");
                json.put("message", e.getMessage());
                MiscUtils.getLogger().error("Failed to transfer file. ", e);
            }
        }

        // TODO add a checksum: Uploaded templates will be checksum hashed, the hash will be stored in an
        // indicatorTemplate column.  Every IndicatorTemplate checksum will be compared to the upload for duplicates.
        // The DashboarManager will contain a method. This class will return an error to the user.

        // TODO run the contained MySQL queries to check for syntax errors and whatever else may be broken.
        // The DashboarManager will contain a method. This class will return an error to the user.

        if (filebytes != null) {
            Boolean isOscarXml = false;
            URL schemaSource = Thread.currentThread().getContextClassLoader().getResource("indicatorXMLTemplates/IndicatorXMLTemplateSchema.xsd");

            try {
                XMLErrorHandler errorHandler = new XMLErrorHandler();
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setValidating(true);
                factory.setNamespaceAware(true);
                
                try {
                    // Disable external entities to prevent XXE attacks
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                
                } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                    MiscUtils.getLogger().error("Failed to set XML parser features to prevent XXE attacks", e);
                    throw new RuntimeException(e);
                }
                
                SAXParser parser = factory.newSAXParser();
                // Configure SAXReader to prevent XXE attacks
                SAXReader xmlReader = new SAXReader();
                
                try {
                    xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                    MiscUtils.getLogger().error("Failed to set XML parser features to prevent XXE attacks", e);
                    throw new RuntimeException(e);
                }

                Document xmlDocument = (Document) xmlReader.read(Files.newBufferedReader(indicatorTemplateFile.toPath()));
                parser.setProperty(
                        "http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                        "http://www.w3.org/2001/XMLSchema");
                parser.setProperty(
                        "http://java.sun.com/xml/jaxp/properties/schemaSource",
                        "file:" + schemaSource.getPath());
                SAXValidator validator = new SAXValidator(parser.getXMLReader());
                validator.setErrorHandler(errorHandler);
                validator.validate(xmlDocument);
                if (!errorHandler.getErrors().hasContent()) {
                    isOscarXml = true;
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to transfer file. ", e);
            }

            if (isOscarXml) {
                message = dashboardManager.importIndicatorTemplate(loggedInInfo, filebytes);
                try {
                    json = (ObjectNode) objectMapper.readTree(message);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Failed to read json. ", e);
                }
            } else {
                json = objectMapper.createObjectNode();
                json.put("status", "error");
                json.put("message", "There is a validation error");
            }
        }

        Map<String, String> messageMap = new HashMap<String, String>();
        messageMap.put("status", json.get("status").asText());
        messageMap.put("message", json.get("message").asText());

        setRequest(loggedInInfo, request, messageMap);

        return SUCCESS;
    }


    @SuppressWarnings("unused")
    public String exportTemplate() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.READ, null)) {
            MiscUtils.getLogger().warn("Unauthorized. Missing _dasboardManager object for user "
                    + loggedInInfo.getLoggedInProviderNo() + " while downloading indicator template.");
            return "unauthorized";
        }

        String indicator = request.getParameter("indicatorId");
        String indicatorName = request.getParameter("indicatorName");

        int indicatorId = 0;
        String xmlTemplate = null;
        OutputStream outputStream = null;

        if (indicatorName == null || indicatorName.isEmpty()) {
            indicatorName = "indicator_template-" + System.currentTimeMillis() + ".xml";
        } else {
            // Sanitize filename to prevent HTTP response splitting
            String baseName = sanitizeHeaderValue(indicatorName);
            indicatorName = baseName + ".xml";
        }
        
        xmlTemplate = dashboardManager.exportIndicatorTemplate(loggedInInfo, Integer.parseInt(indicator));

        if (xmlTemplate != null) {

            response.setContentType("text/xml");
            response.setHeader("Content-disposition", "attachment; filename=" + indicatorName);

            try {
                outputStream = response.getOutputStream();
                outputStream.write(xmlTemplate.getBytes());
            } catch (Exception e) {
                MiscUtils.getLogger().error("File not found", e);
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        logger.error("Failed to close output stream", e);
                    }
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    public String assignDashboard() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }
        String dashboard = request.getParameter("indicatorDashboardId");
        String indicator = request.getParameter("indicatorId");
        int dashboardId = 0;
        int indicatorId = 0;

        ObjectNode jsonObject = objectMapper.createObjectNode();

        if (dashboard != null && !dashboard.isEmpty()) {
            dashboardId = Integer.parseInt(dashboard);
        }

        if (indicator != null && !indicator.isEmpty()) {
            indicatorId = Integer.parseInt(indicator);
        }

        if (dashboardManager.assignIndicatorToDashboard(loggedInInfo, dashboardId, indicatorId)) {
            jsonObject.put("success", "true");
        } else {
            jsonObject.put("success", "false");
        }

        try {
            response.getWriter().write(jsonObject.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("JSON response failed", e);
            return "error";
        }

        return null;
    }


    @SuppressWarnings("unused")
    public String saveDashboard() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String action = null;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        String dashboardId = request.getParameter("dashboardId");
        String dashboardName = request.getParameter("dashboardName");
        String dashboardActive = request.getParameter("dashboardActive");
        String dashboardDescription = request.getParameter("dashboardDescription");
        Integer id = null;
        Boolean active = Boolean.FALSE;

        if (dashboardId != null && !dashboardId.isEmpty()) {
            id = Integer.parseInt(dashboardId);
        }

        if (dashboardActive != null && !dashboardActive.isEmpty()) {
            active = Boolean.TRUE;
        }

        // TODO check the current dashboard table for duplicates.
        // strip out all punctuation and spaces, all names to lower case.
        // Method in DashboardManager, return message to user from this class.

        Dashboard dashboard = new Dashboard();
        dashboard.setId(id);
        dashboard.setName(dashboardName);
        dashboard.setDescription(dashboardDescription);
        dashboard.setActive(active);
        dashboard.setCreator(loggedInInfo.getLoggedInProviderNo());
        dashboard.setEdited(new Date(System.currentTimeMillis()));
        dashboard.setLocked(Boolean.FALSE);

        if (dashboardManager.addDashboard(loggedInInfo, dashboard)) {
            setRequest(loggedInInfo, request);
            action = SUCCESS;
        } else {
            action = ERROR;
        }

        return action;
    }

    @SuppressWarnings("unused")
    public String toggleActive() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardManager", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        String objectClassName = request.getParameter("objectClassName");
        String objectId = request.getParameter("objectId");
        String activeState = request.getParameter("active");
        Boolean active = Boolean.FALSE;
        int id = 0;

        if (objectId != null && !objectId.isEmpty()) {
            id = Integer.parseInt(objectId);
        }

        if (activeState != null) {
            active = Boolean.parseBoolean(activeState);
        }

        if (id > 0 && objectClassName != null) {
            dashboardManager.toggleStatus(loggedInInfo, id, ObjectName.valueOf(objectClassName), active);
        }

        return null;
    }

    private void setRequest(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        setRequest(loggedInInfo, request, null);
    }

    /**
     * Helper method to set a response object into the request.
     */
    private void setRequest(LoggedInInfo loggedInInfo, HttpServletRequest request, Object message) {

        List<Dashboard> dashboards = dashboardManager.getDashboards(loggedInInfo);
        List<IndicatorTemplate> indicatorTemplates = dashboardManager.getIndicatorLibrary(loggedInInfo);

        if (dashboards != null) {
            request.setAttribute("dashboards", dashboards);
        }

        if (indicatorTemplates != null) {
            request.setAttribute("indicatorTemplates", indicatorTemplates);
        }

        if (message != null) {
            request.setAttribute("message", message);
        }
    }

    private File indicatorTemplateFile;

    public File getIndicatorTemplateFile() {
        return indicatorTemplateFile;
    }

    public void setIndicatorTemplateFile(File indicatorTemplateFile) {
        this.indicatorTemplateFile = indicatorTemplateFile;
    }
    
    /**
     * Sanitizes a value to be used in an HTTP header to prevent response splitting attacks.
     * Removes all control characters including carriage return and line feed.
     * 
     * @param value The value to sanitize
     * @return The sanitized header value
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "indicator_template";
        }
    
        // Remove dangerous characters
        String sanitized = value
            .replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "")  // Control characters
            .replaceAll("[\"';]", "")  // Quotes and semicolons (break headers)
            .replaceAll("[/\\\\:*?<>|]", "_")  // Filesystem reserved characters
            .replaceAll("\\.xml$", "")  // Remove .xml if already present
            .trim();
        
        // Ensure not empty after sanitization
        if (sanitized.isEmpty()) {
            return "indicator_template";
        }
        
        return sanitized;
    }
}
