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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds a consultation service from Consultation configuration &gt; Add Service.
 *
 * <p>POST only: a GET or HEAD never creates a service. One rendered Add Service form adds one
 * service (issue #4225): the page carries a {@link FormSubmissionGuard} token, so a re-click
 * during a slow save or a reload of the result page (which is rendered straight from the POST)
 * answers with the first add's own confirmation instead of adding a second service with the same
 * name. The confirmation page renders a fresh form, so adding another service still works.</p>
 */
public class EctConAddService2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private ConsultationServiceDao consultationServiceDao = (ConsultationServiceDao) SpringUtils.getBean(ConsultationServiceDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute()
            throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_admin,_admin.consult", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.consult)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // A post without a token keeps the previous behaviour.
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.CONSULTATION_SERVICE_ADD,
                null, FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case PROCEED, ABSENT -> { }
            case ALREADY_SAVED -> {
                // Confirm what the first add saved, never what this repeat carries: a row that cannot be
                // found is answered as an unconfirmed save rather than echoing the repeat's own entry.
                ConsultationServices saved = consultationServiceDao.find(ConversionUtils.fromIntString(attempt.result()));
                if (saved == null) {
                    return FormSubmissionGuard.refuse(request, response, FormSubmissionGuard.Verdict.UNCONFIRMED);
                }
                request.setAttribute("SERVADD", saved.getServiceDesc());
                return SUCCESS;
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }

        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            ConsultationServices cs = new ConsultationServices();
            cs.setActive(ConsultationServiceDao.ACTIVE);
            cs.setServiceDesc(service);
            // The insert commits in a transaction the claim observes: a rolled-back add leaves the
            // form submittable, and one whose commit could not be confirmed never does.
            new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                if (claim != null) {
                    claim.storageStarted();
                }
                consultationServiceDao.persist(cs);
            });
            // Only an insert that produced an id can be shown again; without one the claim ends unconfirmed.
            if (claim != null && cs.getId() != null && cs.getId() > 0) {
                claim.saved(String.valueOf(cs.getId()));
            }
        }
        request.setAttribute("SERVADD", service);

        return SUCCESS;
    }

    public String getService() {
        if (service == null)
            service = new String();
        return service;
    }

    @StrutsParameter
    public void setService(String str) {
        service = str;
    }

    String service;
}
