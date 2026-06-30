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

package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Handles Rich Text Letter attachment submissions for saved eForms.
 *
 * <p>The action validates the saved eForm identifier and demographic number, then fans the
 * incoming checkbox selections back out across the five attachment families supported by the
 * popup: documents, labs, HRM reports, eForms, and encounter forms.</p>
 */
public class EFormAttachDocs2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final transient SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final transient DocumentAttachmentManager documentAttachmentManager = SpringUtils.getBean(DocumentAttachmentManager.class);

    /**
     * Validates the request identifiers and persists the selected attachment ids for each
     * supported document family.
     *
     * @return {@link ActionSupport#NONE} after writing the HTTP response directly
     */
    @Override
    public String execute() throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "u", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        if (StringUtils.isBlank(requestId) || !StringUtils.isNumeric(requestId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing requestId");
            return NONE;
        }
        if (StringUtils.isBlank(demoNo) || !StringUtils.isNumeric(demoNo)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing demoNo");
            return NONE;
        }

        String effectiveProviderNo = StringUtils.defaultIfBlank(providerNo, loggedInInfo.getLoggedInProviderNo());
        Integer requestIdInt;
        Integer demographicNoInt;
        try {
            requestIdInt = Integer.valueOf(requestId);
            demographicNoInt = Integer.valueOf(demoNo);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid requestId or demoNo");
            return NONE;
        }

        attachSelections(loggedInInfo, DocumentType.DOC, collectDocIds(), "_edoc", effectiveProviderNo, requestIdInt, demographicNoInt);
        attachSelections(loggedInInfo, DocumentType.LAB, collectTypedIds("labNo", 'L'), "_lab", effectiveProviderNo, requestIdInt, demographicNoInt);
        attachSelections(loggedInInfo, DocumentType.HRM, collectTypedIds("hrmNo", 'H'), "_hrm", effectiveProviderNo, requestIdInt, demographicNoInt);
        attachSelections(loggedInInfo, DocumentType.EFORM, collectTypedIds("eFormNo", 'E'), "_eform", effectiveProviderNo, requestIdInt, demographicNoInt);
        attachSelections(loggedInInfo, DocumentType.FORM, collectTypedIds("formNo", 'F'), "_form", effectiveProviderNo, requestIdInt, demographicNoInt);

        writeOkResponse();
        return NONE;
    }

    private void attachSelections(LoggedInInfo loggedInInfo, DocumentType documentType, String[] submittedIds,
            String requiredReadPrivilege, String effectiveProviderNo, Integer requestIdInt, Integer demographicNoInt) {
        String[] effectiveSelections = securityInfoManager.hasPrivilege(loggedInInfo, requiredReadPrivilege, "r", null)
                ? submittedIds
                : getExistingAttachmentIds(loggedInInfo, documentType, requestIdInt, demographicNoInt);
        documentAttachmentManager.attachToEForm(loggedInInfo, documentType, effectiveSelections, effectiveProviderNo, requestIdInt, demographicNoInt);
    }

    private String[] getExistingAttachmentIds(LoggedInInfo loggedInInfo, DocumentType documentType, Integer requestIdInt, Integer demographicNoInt) {
        List<String> existingAttachmentIds = documentAttachmentManager.getEFormAttachments(loggedInInfo, requestIdInt, documentType, demographicNoInt);
        return existingAttachmentIds.toArray(new String[0]);
    }

    private String[] collectDocIds() {
        Set<String> values = new LinkedHashSet<>();
        addTypedValues(values, request.getParameterValues("docNo"));
        if (attachedDocs != null) {
            for (String value : attachedDocs) {
                String docId = normalizeAttachedDocId(value);
                if (docId != null) {
                    values.add(docId);
                }
            }
        }
        return values.toArray(new String[0]);
    }

    private String normalizeAttachedDocId(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        if (!Character.isLetter(value.charAt(0))) {
            return value;
        }
        return value.charAt(0) == 'D' && value.length() > 1 ? value.substring(1) : null;
    }

    private String[] collectTypedIds(String parameterName, char legacyPrefix) {
        Set<String> values = new LinkedHashSet<>();
        addTypedValues(values, request.getParameterValues(parameterName));
        if (attachedDocs != null) {
            for (String value : attachedDocs) {
                if (StringUtils.isBlank(value) || value.length() < 2) {
                    continue;
                }
                if (value.charAt(0) == legacyPrefix) {
                    values.add(value.substring(1));
                }
            }
        }
        return values.toArray(new String[0]);
    }

    private void addTypedValues(Set<String> values, String[] parameters) {
        if (parameters == null) {
            return;
        }
        for (String value : parameters) {
            if (StringUtils.isNotBlank(value)) {
                values.add(value);
            }
        }
    }

    private void writeOkResponse() throws IOException {
        response.setContentType("text/plain");
        response.getWriter().write("ok");
        response.getWriter().flush();
    }

    private String requestId;
    private String demoNo;
    private String providerNo;
    private String[] attachedDocs;

    public String getRequestId() {
        return requestId;
    }

    @StrutsParameter
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDemoNo() {
        return demoNo;
    }

    @StrutsParameter
    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    @StrutsParameter
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String[] getAttachedDocs() {
        return attachedDocs;
    }

    @StrutsParameter
    public void setAttachedDocs(String[] attachedDocs) {
        this.attachedDocs = attachedDocs;
    }
}
