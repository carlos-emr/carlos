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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only endpoint that refiles a document to a different queue. Replaces
 * the GET-triggerable scriptlet in the legacy {@code documentBrowser.jsp}.
 * {@code ManageDocument2Action.refileDocumentAjax} handles the AJAX-from-UI
 * path; this action is the redirect-after-post equivalent used by
 * documentBrowser's full-page submit flow.
 *
 * Refile exceptions are caught and surfaced via an {@code errorMessage} query
 * parameter on the redirect, matching the behavior of the legacy scriptlet.
 */
public class DocumentRefile2Action extends ActionSupport {

    private static final String METHOD_NOT_ALLOWED = "methodNotAllowed";

    private String refileDocumentNo;
    private String queueId;
    private String demographicID;
    private String function;
    private String doctype;
    private String functionid;
    private String categorykey;
    private String viewstatus;

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

        String errorMessage = null;
        if (refileDocumentNo != null && !refileDocumentNo.isEmpty()) {
            if (!isPositiveInteger(refileDocumentNo) || !isPositiveInteger(queueId)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid refileDocumentNo or queueId");
                return NONE;
            }
            try {
                refileDocument(refileDocumentNo, queueId);
            } catch (SecurityException | Error e) {
                // Never swallow auth failures or JVM errors.
                throw e;
            } catch (Exception e) {
                // Log with IDs + provider (non-PHI) for ops triage. Show the user
                // a generic message — e.getMessage() from EDocUtil.refileDocument
                // can contain the document description, which is PHI-adjacent.
                MiscUtils.getLogger().error(
                    "refileDocument failed docNo=" + refileDocumentNo
                    + " queueId=" + queueId
                    + " provider=" + loggedInInfo.getLoggedInProviderNo(), e);
                errorMessage = "Refile failed. Please contact support if the issue persists.";
            }
        }

        String redirect = new RedirectUrlBuilder(
                    request.getContextPath() + "/documentManager/ViewDocumentBrowser")
                .param("demographicID", demographicID)
                .param("function", function)
                .param("doctype", doctype)
                .param("functionid", functionid)
                .param("categorykey", categorykey)
                .param("viewstatus", viewstatus)
                .param("errorMessage", errorMessage)
                .toString();
        response.sendRedirect(redirect);
        return NONE;
    }

    /** Test seam: delegates to {@link EDocUtil#refileDocument(String,String)}. */
    protected void refileDocument(String docNo, String queue) throws Exception {
        EDocUtil.refileDocument(docNo, queue);
    }

    private static boolean isPositiveInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
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
    public String getViewstatus() { return viewstatus; }
    @StrutsParameter public void setViewstatus(String v) { this.viewstatus = v; }
}
