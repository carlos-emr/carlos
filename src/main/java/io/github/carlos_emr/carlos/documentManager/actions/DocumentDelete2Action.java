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
 * POST-only endpoint that soft-deletes a document. Replaces the
 * GET-triggerable scriptlet in the old {@code documentBrowser.jsp} and
 * {@code documentReport.jsp} which ran
 * {@code EDocUtil.deleteDocument(request.getParameter("delDocumentNo"))}
 * behind only an {@code _edoc r} taglib gate -- allowing any user with
 * document-read rights to destroy documents via a crafted GET link.
 *
 * The result forwards back to the document report view so the browser
 * refreshes with the document removed from the visible list.
 */
public final class DocumentDelete2Action extends ActionSupport {

    private String delDocumentNo;
    private String function;
    private String doctype;
    private String functionid;
    private String curUser;
    private String view;

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

        if (delDocumentNo != null && !delDocumentNo.isEmpty()) {
            EDocUtil.deleteDocument(delDocumentNo);
        }

        // Redirect back to the documentReport view with the original query
        // string so the browser refreshes the list after the delete.
        StringBuilder url = new StringBuilder(request.getContextPath())
                .append("/documentManager/ViewDocumentReport.do");
        String sep = "?";
        if (function != null) { url.append(sep).append("function=").append(e(function)); sep = "&"; }
        if (doctype != null) { url.append(sep).append("doctype=").append(e(doctype)); sep = "&"; }
        if (functionid != null) { url.append(sep).append("functionid=").append(e(functionid)); sep = "&"; }
        if (curUser != null) { url.append(sep).append("curUser=").append(e(curUser)); sep = "&"; }
        if (view != null) { url.append(sep).append("view=").append(e(view)); }

        response.sendRedirect(url.toString());
        return NONE;
    }

    private static String e(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getDelDocumentNo() { return delDocumentNo; }
    @StrutsParameter public void setDelDocumentNo(String v) { this.delDocumentNo = v; }

    public String getFunction() { return function; }
    @StrutsParameter public void setFunction(String v) { this.function = v; }

    public String getDoctype() { return doctype; }
    @StrutsParameter public void setDoctype(String v) { this.doctype = v; }

    public String getFunctionid() { return functionid; }
    @StrutsParameter public void setFunctionid(String v) { this.functionid = v; }

    public String getCurUser() { return curUser; }
    @StrutsParameter public void setCurUser(String v) { this.curUser = v; }

    public String getView() { return view; }
    @StrutsParameter public void setView(String v) { this.view = v; }
}
