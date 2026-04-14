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
 * POST-only endpoint that restores a soft-deleted document. Replaces the
 * GET-triggerable scriptlet in {@code documentReport.jsp} (legacy path;
 * {@code documentBrowser.jsp} has the equivalent path). Requires
 * {@code _admin.edocdelete w} since undelete is an admin-restricted
 * operation (mirrors the original taglib requirement).
 */
public final class DocumentUndelete2Action extends ActionSupport {

    private String undelDocumentNo;
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
        if (loggedInInfo == null
                || !sim.hasPrivilege(loggedInInfo, "_admin.edocdelete", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.edocdelete w)");
        }

        if (undelDocumentNo != null && !undelDocumentNo.isEmpty()) {
            EDocUtil.undeleteDocument(undelDocumentNo);
        }

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

    public String getUndelDocumentNo() { return undelDocumentNo; }
    @StrutsParameter public void setUndelDocumentNo(String v) { this.undelDocumentNo = v; }

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
