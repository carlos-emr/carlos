/**
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * This software was written for
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.provider.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.DocumentDescriptionTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DocumentDescriptionTemplate2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private DocumentDescriptionTemplateDao documentDescriptionTemplateDao = SpringUtils.getBean(DocumentDescriptionTemplateDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String method = request.getParameter("method");
        if (isMutatingMethod(method) && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                        "GET requests are not allowed on this endpoint. Use POST.");
            } catch (IOException e) {
                throw new IllegalStateException("Unable to reject document-description mutation", e);
            }
            return NONE;
        }
        if ("getDocumentDescriptionFromDocType".equals(method)) {
            return getDocumentDescriptionFromDocType();
        } else if ("getDocumentDescriptionFromId".equals(method)) {
            return getDocumentDescriptionFromId();
        } else if ("addDocumentDescription".equals(method)) {
            return addDocumentDescription();
        } else if ("updateDocumentDescription".equals(method)) {
            return updateDocumentDescription();
        } else if ("deleteDocumentDescription".equals(method)) {
            return deleteDocumentDescription();
        } else if ("saveDocumentDescriptionTemplatePreference".equals(method)) {
            return saveDocumentDescriptionTemplatePreference();
        } 
        return SUCCESS;
    }

    private static boolean isMutatingMethod(String method) {
        return "addDocumentDescription".equals(method)
                || "updateDocumentDescription".equals(method)
                || "deleteDocumentDescription".equals(method)
                || "saveDocumentDescriptionTemplatePreference".equals(method);
    }

    public String getDocumentDescriptionFromDocType() {
        String docType = request.getParameter("doctype");
        String providerNo = null;
        String useDocumentDescriptionTemplateType = request.getParameter("useDocumentDescriptionTemplateType");
        if (useDocumentDescriptionTemplateType != null && useDocumentDescriptionTemplateType.equals(UserProperty.USER)) {
            providerNo = request.getParameter("providerNo");
        }
        List<DocumentDescriptionTemplate> documentDescriptionTemplate = documentDescriptionTemplateDao.findByDocTypeAndProviderNo(docType, providerNo);
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("documentDescriptionTemplate", documentDescriptionTemplate);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes("UTF-8"));
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return null;
    }

    public String getDocumentDescriptionFromId() {
        String ids = request.getParameter("id");
        Integer id = Integer.valueOf(ids);
        DocumentDescriptionTemplate documentDescriptionTemplate = documentDescriptionTemplateDao.find(id);
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("documentDescriptionTemplate", documentDescriptionTemplate);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes("UTF-8"));
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return null;
    }

    public String addDocumentDescription() {
        String docType = request.getParameter("doctype");
        String description = request.getParameter("description");
        String descriptionShortcut = request.getParameter("shortcut");
        String providerNo = request.getParameter("providerNo").equals("null") ? null : request.getParameter("providerNo");
        DocumentDescriptionTemplate documentDescriptionTemplate = new DocumentDescriptionTemplate();
        documentDescriptionTemplate.setDescription(description);
        documentDescriptionTemplate.setDescriptionShortcut(descriptionShortcut);
        documentDescriptionTemplate.setDocType(docType);
        documentDescriptionTemplate.setProviderNo(providerNo);
        this.documentDescriptionTemplateDao.persist(documentDescriptionTemplate);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        LogAction.addLog(loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null, LogConst.ADD, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, LogSafe.sanitize(providerNo), request.getRemoteAddr(), null, "[" + LogSafe.sanitize(docType) + "] " + LogSafe.sanitize(descriptionShortcut) + " | " + LogSafe.sanitize(description));
        return null;
    }

    public String updateDocumentDescription() {
        String ids = request.getParameter("id");
        Integer id = Integer.valueOf(ids);

        String docType = request.getParameter("doctype");
        String description = request.getParameter("description");
        String descriptionShortcut = request.getParameter("shortcut");
        String providerNo = request.getParameter("providerNo").equals("null") ? null : request.getParameter("providerNo");
        DocumentDescriptionTemplate documentDescriptionTemplate = new DocumentDescriptionTemplate();
        documentDescriptionTemplate.setDescription(description);
        documentDescriptionTemplate.setDescriptionShortcut(descriptionShortcut);
        documentDescriptionTemplate.setDocType(docType);
        documentDescriptionTemplate.setId(id);
        documentDescriptionTemplate.setProviderNo(providerNo);
        this.documentDescriptionTemplateDao.merge(documentDescriptionTemplate);
        LoggedInInfo loggedInInfoUpdate = LoggedInInfo.getLoggedInInfoFromSession(request);
        LogAction.addLog(loggedInInfoUpdate != null ? loggedInInfoUpdate.getLoggedInProviderNo() : null, LogConst.UPDATE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, LogSafe.sanitize(providerNo), request.getRemoteAddr(), null, LogSafe.sanitize(ids) + " [" + LogSafe.sanitize(docType) + "] " + LogSafe.sanitize(descriptionShortcut) + " | " + LogSafe.sanitize(description));
        return null;
    }

    public String deleteDocumentDescription() {
        String ids = request.getParameter("id");
        Integer id = Integer.valueOf(ids);
        this.documentDescriptionTemplateDao.remove(id);
        LoggedInInfo loggedInInfoDelete = LoggedInInfo.getLoggedInInfoFromSession(request);
        LogAction.addLog(loggedInInfoDelete != null ? loggedInInfoDelete.getLoggedInProviderNo() : null, LogConst.DELETE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, LogSafe.sanitize(ids), request.getRemoteAddr());
        return null;
    }

    public String saveDocumentDescriptionTemplatePreference() {

        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String DocumentDescriptionShorcut = request.getParameter("defaultShortcut");
        if (DocumentDescriptionShorcut == null || !DocumentDescriptionShorcut.equals(UserProperty.USER)) {
            DocumentDescriptionShorcut = UserProperty.CLINIC;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String provider = loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null;
        UserProperty uProperty = userPropertyDAO.getProp(provider, UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
        if (uProperty == null) {
            uProperty = new UserProperty();
            uProperty.setProviderNo(provider);
            uProperty.setName(UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
        }
        uProperty.setValue(DocumentDescriptionShorcut);
        userPropertyDAO.saveProp(uProperty);
        LogAction.addLog(provider, LogConst.UPDATE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATEPREFERENCE, DocumentDescriptionShorcut, request.getRemoteAddr());
        return null;
    }
}
