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

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only endpoint that restores a soft-deleted document. Replaces the
 * GET-triggerable scriptlet in {@code documentReport.jsp} and
 * {@code documentBrowser.jsp}.
 *
 * Authorization model: the caller must satisfy one of two disjoint branches
 * — admins with {@code _admin.edocdelete w} may undelete any document;
 * otherwise the caller must hold {@code _edoc w} AND their provider number
 * must match the document's {@code creatorId}. This preserves the legacy
 * creator-undelete path while closing the GET-triggerable vector.
 *
 * Redirects back to the caller's view: {@code source=browser} returns to
 * {@code ViewDocumentBrowser}, otherwise to {@code ViewDocumentReport}.
 */
public class DocumentUndelete2Action extends ActionSupport {

    private static final String METHOD_NOT_ALLOWED = "methodNotAllowed";

    private String undelDocumentNo;
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
        if (loggedInInfo == null) {
            throw new SecurityException("not logged in");
        }
        boolean isAdmin = sim.hasPrivilege(loggedInInfo, "_admin.edocdelete", "w", null);
        boolean hasEdocWrite = sim.hasPrivilege(loggedInInfo, "_edoc", "w", null);
        if (!isAdmin && !hasEdocWrite) {
            throw new SecurityException("missing required sec object (_admin.edocdelete w or _edoc w)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return METHOD_NOT_ALLOWED;
        }

        if (undelDocumentNo != null && !undelDocumentNo.isEmpty()) {
            if (!isPositiveInteger(undelDocumentNo)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid undelDocumentNo");
                return NONE;
            }
            if (!isAdmin) {
                EDoc doc = loadDoc(undelDocumentNo);
                String providerNo = loggedInInfo.getLoggedInProviderNo();
                if (doc == null
                        || doc.getCreatorId() == null
                        || providerNo == null
                        || !providerNo.equals(doc.getCreatorId())) {
                    throw new SecurityException("only admins or the document creator may undelete");
                }
            }
            undeleteDocument(undelDocumentNo);
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

    /** Test seam: delegates to {@link EDocUtil#getDoc(String)}. */
    protected EDoc loadDoc(String docNo) {
        return EDocUtil.getDoc(docNo);
    }

    /** Test seam: delegates to {@link EDocUtil#undeleteDocument(String)}. */
    protected void undeleteDocument(String docNo) {
        EDocUtil.undeleteDocument(docNo);
    }

    private static boolean isPositiveInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
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

    public String getViewstatus() { return viewstatus; }
    @StrutsParameter public void setViewstatus(String v) { this.viewstatus = v; }

    public String getCategorykey() { return categorykey; }
    @StrutsParameter public void setCategorykey(String v) { this.categorykey = v; }

    public String getSource() { return source; }
    @StrutsParameter public void setSource(String v) { this.source = v; }
}
