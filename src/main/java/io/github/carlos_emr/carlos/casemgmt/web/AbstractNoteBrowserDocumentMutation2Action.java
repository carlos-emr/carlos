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
package io.github.carlos_emr.carlos.casemgmt.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

abstract class AbstractNoteBrowserDocumentMutation2Action extends ActionSupport {

    private static final String METHOD_NOT_ALLOWED = "methodNotAllowed";
    private static final String GENERIC_ERROR_MESSAGE = "Document update failed.";

    private String demographicNo;
    private String view;
    private String viewstatus;
    private String sortorder;
    private String mutationErrorMessage = "";

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        SecurityInfoManager sim = SpringUtils.getBean(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !sim.hasPrivilege(loggedInInfo, "_eChart", "w", null)) {
            throw new SecurityException("missing required sec object (_eChart w)");
        }

        if (!"POST".equals(request.getMethod())) {
            return METHOD_NOT_ALLOWED;
        }

        mutationErrorMessage = "";
        if (!hasMutationParameter()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidParameterMessage());
            return NONE;
        }
        if (!hasValidMutationParameters()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalidParameterMessage());
            return NONE;
        }
        try {
            mutateDocument();
        } catch (SecurityException e) {
            throw e;
        } catch (DocumentMutationException e) {
            if (!handlesMutationException()) {
                throw e;
            }
            MiscUtils.getLogger().error(logMessage(), e);
            mutationErrorMessage = GENERIC_ERROR_MESSAGE;
        }

        return SUCCESS;
    }

    protected abstract boolean hasMutationParameter();
    protected abstract boolean hasValidMutationParameters();
    protected abstract String invalidParameterMessage();
    protected abstract void mutateDocument() throws DocumentMutationException;

    protected boolean handlesMutationException() {
        return false;
    }

    protected String logMessage() {
        return getClass().getSimpleName() + " failed";
    }

    protected static boolean isPositiveInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        boolean hasNonZeroDigit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
            if (c != '0') {
                hasNonZeroDigit = true;
            }
        }
        return hasNonZeroDigit;
    }

    public String getDemographicNo() { return demographicNo; }
    @StrutsParameter public void setDemographicNo(String v) { this.demographicNo = v; }
    public String getView() { return view; }
    @StrutsParameter public void setView(String v) { this.view = v; }
    public String getViewstatus() { return viewstatus; }
    @StrutsParameter public void setViewstatus(String v) { this.viewstatus = v; }
    public String getSortorder() { return sortorder; }
    @StrutsParameter public void setSortorder(String v) { this.sortorder = v; }
    public String getMutationErrorMessage() { return mutationErrorMessage; }

    protected static class DocumentMutationException extends Exception {
        private static final long serialVersionUID = 1L;

        DocumentMutationException(Throwable cause) {
            super(cause);
        }
    }
}
