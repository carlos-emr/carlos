/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * POST-only endpoint that refiles a document to a different queue. Replaces
 * the GET-triggerable scriptlet in the old {@code documentBrowser.jsp}.
 * {@code ManageDocument2Action.refileDocumentAjax} handles the AJAX-from-UI
 * path; this action is the redirect-after-post equivalent used by
 * documentBrowser's full-page submit flow.
 */
public final class DocumentRefile2Action extends ActionSupport {

    private String refileDocumentNo;
    private String queueId;
    private String demographicID;
    private String function;
    private String doctype;
    private String functionid;
    private String categorykey;

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc w)");
        }

        if (refileDocumentNo != null && !refileDocumentNo.isEmpty()) {
            EDocUtil.refileDocument(refileDocumentNo, queueId);
        }

        StringBuilder url = new StringBuilder(request.getContextPath())
                .append("/documentManager/ViewDocumentBrowser.do");
        String sep = "?";
        if (demographicID != null) { url.append(sep).append("demographicID=").append(e(demographicID)); sep = "&"; }
        if (function != null) { url.append(sep).append("function=").append(e(function)); sep = "&"; }
        if (doctype != null) { url.append(sep).append("doctype=").append(e(doctype)); sep = "&"; }
        if (functionid != null) { url.append(sep).append("functionid=").append(e(functionid)); sep = "&"; }
        if (categorykey != null) { url.append(sep).append("categorykey=").append(e(categorykey)); }

        response.sendRedirect(url.toString());
        return NONE;
    }

    private static String e(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getRefileDocumentNo() { return refileDocumentNo; }
    @StrutsParameter public void setRefileDocumentNo(String v) { this.refileDocumentNo = v; }
    public String getQueueId() { return queueId; }
    @StrutsParameter public void setQueueId(String v) { this.queueId = v; }
    public String getDemographicID() { return demographicID; }
    @StrutsParameter public void setDemographicID(String v) { this.demographicID = v; }
    public String getFunction() { return function; }
    @StrutsParameter public void setFunction(String v) { this.function = v; }
    public String getDoctype() { return doctype; }
    @StrutsParameter public void setDoctype(String v) { this.doctype = v; }
    public String getFunctionid() { return functionid; }
    @StrutsParameter public void setFunctionid(String v) { this.functionid = v; }
    public String getCategorykey() { return categorykey; }
    @StrutsParameter public void setCategorykey(String v) { this.categorykey = v; }
}
