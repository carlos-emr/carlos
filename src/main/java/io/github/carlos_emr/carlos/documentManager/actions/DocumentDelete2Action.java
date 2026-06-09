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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only endpoint that soft-deletes a document. Replaces the
 * GET-triggerable scriptlet in the legacy {@code documentBrowser.jsp} and
 * {@code documentReport.jsp} which ran
 * {@code EDocUtil.deleteDocument(request.getParameter("delDocumentNo"))}
 * behind only an {@code _edoc r} taglib gate, allowing any user with
 * document-read rights to destroy documents via a crafted GET link.
 *
 * Redirects back to the caller's view: {@code source=browser} returns to
 * {@code ViewDocumentBrowser} (preserving browser filter state), otherwise
 * to {@code ViewDocumentReport}.
 */
public class DocumentDelete2Action extends ActionSupport {

    private static final String METHOD_NOT_ALLOWED = "methodNotAllowed";

    private String delDocumentNo;
    private String function;
    private String doctype;
    private String functionid;
    private String curUser;
    private String view;
    private String viewstatus;
    private String categorykey;
    private String source;

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc w)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return METHOD_NOT_ALLOWED;
        }

        if (delDocumentNo != null && !delDocumentNo.isEmpty()) {
            if (!isPositiveInteger(delDocumentNo)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid delDocumentNo");
                return NONE;
            }
            deleteDocument(delDocumentNo);
        }

        String target = "browser".equalsIgnoreCase(source)
                ? "/documentManager/ViewDocumentBrowser"
                : "/documentManager/ViewDocumentReport";
        String redirect = new RedirectUrlBuilder(request.getContextPath() + target)
                .param("function", function)
                .param("doctype", doctype)
                .param("functionid", functionid)
                .param("curUser", curUser)
                .param("view", view)
                .param("viewstatus", viewstatus)
                .param("categorykey", categorykey)
                .toString();
        response.sendRedirect(redirect);
        return NONE;
    }

    /** Test seam: delegates to {@link EDocUtil#deleteDocument(String)}. */
    protected void deleteDocument(String docNo) {
        EDocUtil.deleteDocument(docNo);
    }

    private static boolean isPositiveInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
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

    public String getViewstatus() { return viewstatus; }
    @StrutsParameter public void setViewstatus(String v) { this.viewstatus = v; }

    public String getCategorykey() { return categorykey; }
    @StrutsParameter public void setCategorykey(String v) { this.categorykey = v; }

    public String getSource() { return source; }
    @StrutsParameter public void setSource(String v) { this.source = v; }
}
