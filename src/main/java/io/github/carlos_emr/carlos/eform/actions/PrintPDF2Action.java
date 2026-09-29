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


// form_class - a part of class name
// c_lastVisited, formId - if the form has multiple pages
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.eform.util.EFormPrintPDFUtil;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Legacy {@code eform/efmPrintPDF} route into {@code /eform/createpdf}.
 *
 * <p><strong>This route is unreferenced and its graph branch does not work.</strong> Recorded here
 * because none of it is apparent from the code, and the next reader would otherwise repeat the
 * investigation:</p>
 *
 * <ul>
 *   <li>Nothing calls it. No JSP, JSPF, JS or HTML under {@code src/main/webapp} references
 *       {@code efmPrintPDF}, and neither do any of the 199 third-party eForm packages in the
 *       compatibility corpus — which matters, because eForms are author-written HTML that could
 *       post to it from their own markup.</li>
 *   <li>It carries no eForm identity: there is no {@code fdid} here or in {@link EFormPrintPDFUtil},
 *       and {@code newID} is never assigned, so the redirect always sends {@code formId=0}.</li>
 *   <li>The {@code graph} branch builds its data into request <em>attributes</em> and then
 *       {@link jakarta.servlet.http.HttpServletResponse#sendRedirect} — which discards them.
 *       {@code EFormPDFServlet} reads exactly those attributes, and a redirect also drops the POST
 *       parameters it needs ({@code __template}, {@code __numPages}, {@code __cfgfile}).</li>
 *   <li>Consequently the {@code graph} and {@code printAll} results declared in
 *       {@code struts-eform.xml} never fire: {@code execute()} only ever returns {@code NONE}. The
 *       working equivalent is {@code Frm2Action} ({@code form/formname}), which returns the named
 *       result so the forward preserves the attributes.</li>
 *   <li>Rourke forms — what the graph branch is for — live in the <em>forms</em> subsystem, not
 *       eForms; {@code EFormPrintPDFUtil.getFrmRourkeGraph} reaches into {@code EctFormData} and
 *       {@code formGrowth0_36} from the eform package. This reads as an unfinished bridge between
 *       the two.</li>
 * </ul>
 *
 * <p>The privilege check below was tightened rather than the forward being repaired: making this
 * path work again would revive a PDF route with no completeness gate, no render approval and no
 * advisory notice, for a feature with no caller. Removing the route outright is the right end
 * state, but is a deliberate, user-visible decision — and note that {@code /eform/createpdf} is
 * mapped independently in {@code web.xml}, so deleting this action would not make the servlet
 * unreachable.</p>
 */
public final class PrintPDF2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute() throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Scoped to the requested patient rather than the null (unscoped) demographic this used to
        // pass. Naming another patient's demographic only makes the check harder to pass, never
        // easier, so taking it from the request is safe here.
        //
        // It is still weaker in kind than the modern path: EformDataManagerImpl.createEformPDF
        // resolves the demographic from the STORED eForm and checks against that, which proves the
        // caller may read that record. This route carries no fdid at all — see the class javadoc —
        // so the strongest available check is "may this caller read eForms for this patient".
        String demographicNo = request.getParameter("demographic_no");
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eform", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        int newID = 0;
        try {
            Properties props = new Properties();

            for (Enumeration<?> e = request.getParameterNames(); e.hasMoreElements(); ) {
                String name = (String) e.nextElement();
                props.setProperty(name, request.getParameter(name));
            }

            String submit = request.getParameter("submit");
            log.info("SUBMIT {}", LogSafe.sanitize(submit)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe

            Optional<String> actionValue = findActionValue(submit);
            if (!actionValue.isPresent()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported submit action");
                return NONE;
            }
            String strAction = actionValue.get();

            //if we are graphing, we need to grab info from db and add it to request object
            if ("graph".equals(strAction)) {
                props = EFormPrintPDFUtil.getFrmRourkeGraph(loggedInInfo, props);

                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    String name = (String) e.nextElement();
                    request.setAttribute(name, props.getProperty(name));
                }
            }
            //if we are printing all pages of form, grab info from db and merge with current page info
            else if ("printAll".equals(strAction)) {
                String name;
                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    name = (String) e.nextElement();
                    if (request.getParameter(name) == null)
                        request.setAttribute(name, props.getProperty(name));
                }
            }

            String createPdfPath = request.getContextPath() + "/eform/createpdf";
            String redirectUrl = createActionURL(createPdfPath, strAction, demographicNo, "" + newID);
            response.sendRedirect(redirectUrl);
        } catch (Exception ex) {
            throw new ServletException(ex);
        }

        return NONE;
    }


    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private Optional<String> findActionValue(String submit) {
        if (submit != null && submit.equalsIgnoreCase("graph")) {
            return Optional.of("graph");
        } else if (submit != null && submit.equalsIgnoreCase("printall")) {
            return Optional.of("printAll");
        } else {
            return Optional.empty();
        }
    }

    private String createActionURL(String where, String action, String demoId, String formId) {
        String temp = null;

        if (action.equals("printAll")) {
            temp = where
                    + "?demographic_no=" + SafeEncode.forUriComponent(demoId)
                    + "&formId=" + SafeEncode.forUriComponent(formId);
        } else {
            temp = where;
        }

        return temp;
    }

}
