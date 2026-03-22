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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for managing document description templates.
 *
 * <p>Provides CRUD operations for {@link DocumentDescriptionTemplate} records, which define
 * reusable description shortcuts for clinical documents. Templates can be scoped to a specific
 * provider or shared clinic-wide. Also manages the per-provider preference for which template
 * scope to use by default.</p>
 *
 * <p>Routes via the {@code method} request parameter to the appropriate handler:
 * {@code getDocumentDescriptionFromDocType}, {@code getDocumentDescriptionFromId},
 * {@code addDocumentDescription}, {@code updateDocumentDescription},
 * {@code deleteDocumentDescription}, {@code saveDocumentDescriptionTemplatePreference}.</p>
 *
 * @see DocumentDescriptionTemplate
 * @since 2026-03-17
 */
public class DocumentDescriptionTemplate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private DocumentDescriptionTemplateDao documentDescriptionTemplateDao = SpringUtils.getBean(DocumentDescriptionTemplateDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dispatches to the appropriate handler method based on the {@code method} request parameter.
     *
     * @return String the Struts2 result name, or {@code null} for JSON responses
     */
    public String execute() {
        String method = request.getParameter("method");
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

    /**
     * Retrieves document description templates filtered by document type and optional provider number.
     *
     * <p>If {@code useDocumentDescriptionTemplateType} is {@code "user"}, filters by the
     * specified {@code providerNo}; otherwise returns clinic-wide templates.</p>
     *
     * @return String {@code null} (JSON written directly to response output stream)
     */
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

    /**
     * Retrieves a single document description template by its database ID.
     *
     * @return String {@code null} (JSON written directly to response output stream)
     */
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

    /**
     * Creates a new document description template from request parameters.
     *
     * <p>Reads {@code doctype}, {@code description}, {@code shortcut}, and {@code providerNo}
     * from the request. Logs the creation via {@link LogAction}.</p>
     *
     * @return String {@code null} (no view navigation)
     */
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
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, providerNo, request.getRemoteAddr(), null, "[" + docType + "] " + descriptionShortcut + " | " + description);
        return null;
    }

    /**
     * Updates an existing document description template identified by the {@code id} parameter.
     *
     * <p>Merges the updated fields and logs the modification via {@link LogAction}.</p>
     *
     * @return String {@code null} (no view navigation)
     */
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
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.UPDATE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, providerNo, request.getRemoteAddr(), null, ids + " [" + docType + "] " + descriptionShortcut + " | " + description);
        return null;
    }

    /**
     * Deletes a document description template by the {@code id} request parameter.
     *
     * <p>Logs the deletion via {@link LogAction}.</p>
     *
     * @return String {@code null} (no view navigation)
     */
    public String deleteDocumentDescription() {
        String ids = request.getParameter("id");
        Integer id = Integer.valueOf(ids);
        this.documentDescriptionTemplateDao.remove(id);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.DELETE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, ids, request.getRemoteAddr());
        return null;
    }

    public String saveDocumentDescriptionTemplatePreference() {

        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String DocumentDescriptionShorcut = request.getParameter("defaultShortcut");
        if (DocumentDescriptionShorcut == null || !DocumentDescriptionShorcut.equals(UserProperty.USER)) {
            DocumentDescriptionShorcut = UserProperty.CLINIC;
        }
        String provider = (String) request.getSession().getAttribute("user");
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