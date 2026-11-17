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
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.DocumentDescriptionTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * JSON reads and POST-only writes behind the document description template page and the shortcut
 * buttons of Incoming Docs.
 *
 * <p>Every request first needs some way to read templates; each method then applies the per-entry
 * rule of {@link DocumentDescriptionTemplateAccess}: clinic-wide entries are read by any template
 * reader and changed only with {@code _admin}/{@code _admin.document} write, and a provider's own
 * entries are read and changed only by that provider, with {@code _pref} write for changes. Writes
 * check the stored entry, not the posted owner, so an update cannot move an entry between lists or
 * providers (issue #5057).</p>
 *
 * <p>Responses are written directly; a refused or malformed request gets an error status the page
 * reports, never a partial write.</p>
 */
public class DocumentDescriptionTemplate2Action extends ActionSupport {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Column sizes of documentDescriptionTemplate; a longer value would fail at the database.
    private static final int MAX_DOC_TYPE = 60;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_SHORTCUT = 20;

    // ActionSupport is Serializable; a per-request action never is, so none of this state is.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient DocumentDescriptionTemplateDao documentDescriptionTemplateDao;
    private final transient UserPropertyDAO userPropertyDAO;

    private transient HttpServletRequest request;
    private transient HttpServletResponse response;
    private transient LoggedInInfo loggedInInfo;

    public DocumentDescriptionTemplate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(DocumentDescriptionTemplateDao.class),
                SpringUtils.getBean(UserPropertyDAO.class));
    }

    DocumentDescriptionTemplate2Action(SecurityInfoManager securityInfoManager,
                                       DocumentDescriptionTemplateDao documentDescriptionTemplateDao,
                                       UserPropertyDAO userPropertyDAO) {
        this.securityInfoManager = securityInfoManager;
        this.documentDescriptionTemplateDao = documentDescriptionTemplateDao;
        this.userPropertyDAO = userPropertyDAO;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException {
        request = ServletActionContext.getRequest();
        response = ServletActionContext.getResponse();
        loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        DocumentDescriptionTemplateAccess.requireAnyTemplateRight(securityInfoManager, loggedInInfo);

        String method = request.getParameter("method");
        if (isMutatingMethod(method) && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "GET requests are not allowed on this endpoint. Use POST.");
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
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown document description method");
        return NONE;
    }

    private static boolean isMutatingMethod(String method) {
        return "addDocumentDescription".equals(method)
                || "updateDocumentDescription".equals(method)
                || "deleteDocumentDescription".equals(method)
                || "saveDocumentDescriptionTemplatePreference".equals(method);
    }

    /**
     * Lists one document type's clinic-wide entries, or with
     * {@code useDocumentDescriptionTemplateType=User} the signed-in provider's own entries. A
     * {@code providerNo} naming anyone else is refused rather than silently replaced.
     */
    private String getDocumentDescriptionFromDocType() throws IOException {
        String docType = request.getParameter("doctype");
        String owner = null;
        if (UserProperty.USER.equals(request.getParameter("useDocumentDescriptionTemplateType"))) {
            String named = postedOwner();
            owner = named != null ? named : loggedInInfo.getLoggedInProviderNo();
        }
        DocumentDescriptionTemplateAccess.requireRead(securityInfoManager, loggedInInfo, owner);
        List<DocumentDescriptionTemplate> documentDescriptionTemplate =
                documentDescriptionTemplateDao.findByDocTypeAndProviderNo(docType, owner);
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("documentDescriptionTemplate", documentDescriptionTemplate);
        return writeJson(hm);
    }

    private String getDocumentDescriptionFromId() throws IOException {
        DocumentDescriptionTemplate documentDescriptionTemplate = findRequestedTemplate();
        if (documentDescriptionTemplate == null) {
            return NONE;
        }
        DocumentDescriptionTemplateAccess.requireRead(securityInfoManager, loggedInInfo,
                documentDescriptionTemplate.getProviderNo());
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("documentDescriptionTemplate", documentDescriptionTemplate);
        return writeJson(hm);
    }

    private String addDocumentDescription() throws IOException {
        String providerNo = postedOwner();
        DocumentDescriptionTemplateAccess.requireWrite(securityInfoManager, loggedInInfo, providerNo);
        String docType = request.getParameter("doctype");
        String description = request.getParameter("description");
        String descriptionShortcut = request.getParameter("shortcut");
        if (!hasValidFields(docType, description, descriptionShortcut)) {
            return NONE;
        }
        DocumentDescriptionTemplate documentDescriptionTemplate = new DocumentDescriptionTemplate();
        documentDescriptionTemplate.setDescription(description);
        documentDescriptionTemplate.setDescriptionShortcut(descriptionShortcut);
        documentDescriptionTemplate.setDocType(docType);
        documentDescriptionTemplate.setProviderNo(providerNo);
        this.documentDescriptionTemplateDao.persist(documentDescriptionTemplate);
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, LogSafe.sanitize(providerNo), request.getRemoteAddr(), null, "[" + LogSafe.sanitize(docType) + "] " + LogSafe.sanitize(descriptionShortcut) + " | " + LogSafe.sanitize(description));
        return NONE;
    }

    /**
     * Changes an entry's type, shortcut and description. The entry keeps its stored owner: the
     * write right is checked against it, and a posted {@code providerNo} that names another list
     * is refused.
     */
    private String updateDocumentDescription() throws IOException {
        DocumentDescriptionTemplate documentDescriptionTemplate = findRequestedTemplate();
        if (documentDescriptionTemplate == null) {
            return NONE;
        }
        String providerNo = documentDescriptionTemplate.getProviderNo();
        DocumentDescriptionTemplateAccess.requireWrite(securityInfoManager, loggedInInfo, providerNo);
        if (!Objects.equals(postedOwner(), providerNo)) {
            throw new SecurityException("a document description template keeps its owner");
        }
        String docType = request.getParameter("doctype");
        String description = request.getParameter("description");
        String descriptionShortcut = request.getParameter("shortcut");
        if (!hasValidFields(docType, description, descriptionShortcut)) {
            return NONE;
        }
        documentDescriptionTemplate.setDescription(description);
        documentDescriptionTemplate.setDescriptionShortcut(descriptionShortcut);
        documentDescriptionTemplate.setDocType(docType);
        documentDescriptionTemplate.setLastUpdated(new Date());
        this.documentDescriptionTemplateDao.merge(documentDescriptionTemplate);
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.UPDATE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, LogSafe.sanitize(providerNo), request.getRemoteAddr(), null, documentDescriptionTemplate.getId() + " [" + LogSafe.sanitize(docType) + "] " + LogSafe.sanitize(descriptionShortcut) + " | " + LogSafe.sanitize(description));
        return NONE;
    }

    private String deleteDocumentDescription() throws IOException {
        DocumentDescriptionTemplate documentDescriptionTemplate = findRequestedTemplate();
        if (documentDescriptionTemplate == null) {
            return NONE;
        }
        DocumentDescriptionTemplateAccess.requireWrite(securityInfoManager, loggedInInfo,
                documentDescriptionTemplate.getProviderNo());
        Integer id = documentDescriptionTemplate.getId();
        this.documentDescriptionTemplateDao.remove(id);
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATE, String.valueOf(id), request.getRemoteAddr());
        return NONE;
    }

    private String saveDocumentDescriptionTemplatePreference() {
        DocumentDescriptionTemplateAccess.requireOwnPreferenceWrite(securityInfoManager, loggedInInfo);
        String DocumentDescriptionShorcut = request.getParameter("defaultShortcut");
        if (DocumentDescriptionShorcut == null || !DocumentDescriptionShorcut.equals(UserProperty.USER)) {
            DocumentDescriptionShorcut = UserProperty.CLINIC;
        }
        String provider = loggedInInfo.getLoggedInProviderNo();
        UserProperty uProperty = userPropertyDAO.getProp(provider, UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
        if (uProperty == null) {
            uProperty = new UserProperty();
            uProperty.setProviderNo(provider);
            uProperty.setName(UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
        }
        uProperty.setValue(DocumentDescriptionShorcut);
        userPropertyDAO.saveProp(uProperty);
        LogAction.addLog(provider, LogConst.UPDATE, LogConst.CON_DOCUMENTDESCRIPTIONTEMPLATEPREFERENCE, DocumentDescriptionShorcut, request.getRemoteAddr());
        return NONE;
    }

    /**
     * The list a write or read names: {@code null} for the clinic-wide list (the page posts the
     * literal {@code "null"}), otherwise the provider number as posted.
     */
    private String postedOwner() {
        String providerNo = request.getParameter("providerNo");
        if (providerNo == null || providerNo.isBlank() || "null".equals(providerNo)) {
            return null;
        }
        return providerNo.trim();
    }

    /**
     * Loads the entry the {@code id} parameter names, or answers 400 for a missing or non-numeric
     * id and 404 for an entry that does not exist.
     *
     * @return the entry, or {@code null} when an error response was written
     */
    private DocumentDescriptionTemplate findRequestedTemplate() throws IOException {
        String ids = request.getParameter("id");
        if (ids == null || !ids.matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A document description id is required");
            return null;
        }
        DocumentDescriptionTemplate documentDescriptionTemplate = documentDescriptionTemplateDao.find(Integer.valueOf(ids));
        if (documentDescriptionTemplate == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No such document description");
        }
        return documentDescriptionTemplate;
    }

    private boolean hasValidFields(String docType, String description, String descriptionShortcut) throws IOException {
        if (!fits(docType, MAX_DOC_TYPE) || !fits(description, MAX_DESCRIPTION) || !fits(descriptionShortcut, MAX_SHORTCUT)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "A document type, a shortcut of at most 20 characters and a description of at most 255 are required");
            return false;
        }
        return true;
    }

    private static boolean fits(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private String writeJson(Object value) throws IOException {
        ObjectNode jsonObject = objectMapper.valueToTree(value);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
        return NONE;
    }
}
