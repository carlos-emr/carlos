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


package io.github.carlos_emr.carlos.commn.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetDrugDao;
import io.github.carlos_emr.carlos.commn.dao.FlowSheetDxDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetDrug;
import io.github.carlos_emr.carlos.commn.model.FlowSheetDx;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts 2 action for managing flowsheet drug and diagnosis associations.
 * <p>
 * This action handles saving associations between:
 * <ul>
 * <li>Flowsheets and drugs (via ATC codes)</li>
 * <li>Flowsheets and diagnoses (via diagnosis codes and types)</li>
 * </ul>
 * <p>
 * These associations are used to link clinical flowsheets with relevant
 * medications and diagnoses for patient care tracking and documentation.
 *
 * @since 2006-04-20
 */
public class FlowSheetDrug2Action extends ActionSupport {
    /** A patient number as the flowsheet page posts it. */
    private static final Pattern DEMOGRAPHIC_NO = Pattern.compile("[1-9]\\d{0,8}");

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private FlowSheetDrugDao flowSheetDrugDao = SpringUtils.getBean(FlowSheetDrugDao.class);
    private FlowSheetDxDao flowSheetDxDao = SpringUtils.getBean(FlowSheetDxDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Links a diagnosis or a current drug to a flowsheet for one patient, then returns to the flowsheet.
     * <p>
     * The flowsheet's left panel posts {@code method=dxSave} (a diagnosis) or {@code method=save} (a drug, by ATC
     * code). Patient write rights come first, then the request must be a POST, and a link
     * the flowsheet already has is not saved again. The answer is a redirect back to the flowsheet, so a reload shows
     * the flowsheet instead of posting the link again.
     *
     * <p>Expected request parameters: {@code method}, {@code flowsheet} (the template name), {@code demographic}, and
     * {@code dxCode} plus {@code dxCodeType}, or {@code atcCode}.</p>
     *
     * @return {@link #NONE}: the response is a redirect, or an error status for a refused or malformed request
     * @throws SecurityException if the user lacks write rights for the patient
     */
    // FindSecBugs UNVALIDATED_REDIRECT: the target is this application's flowsheet route, with a numeric patient
    // number and a URL-encoded template name, never a caller-supplied address.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is this application's flowsheet route with a numeric patient and an encoded template, not a caller-supplied address")
    @Override
    public String execute() throws Exception {
        String demographicNo = request.getParameter("demographic");
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        // HTTP method names are case-sensitive (RFC 9110), so only an exact POST may link.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        String method = request.getParameter("method");
        String flowsheet = request.getParameter("flowsheet");
        if (demographicNo == null || !DEMOGRAPHIC_NO.matcher(demographicNo).matches()
                || flowsheet == null || flowsheet.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        int demographic = Integer.parseInt(demographicNo);
        boolean linked;
        if ("save".equals(method)) {
            linked = linkDrug(flowsheet, demographic, request.getParameter("atcCode"));
        } else if ("dxSave".equals(method)) {
            linked = linkDx(flowsheet, demographic, request.getParameter("dxCode"), request.getParameter("dxCodeType"));
        } else {
            linked = false;
        }
        if (!linked) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        response.sendRedirect(request.getContextPath() + "/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no="
                + demographic + "&template=" + URLEncoder.encode(flowsheet, StandardCharsets.UTF_8));
        return NONE;
    }

    /**
     * Links the drug with this ATC code to the flowsheet, unless the flowsheet already links it for the patient.
     *
     * @return true when a link exists afterwards (saved now or before); false for a blank ATC code
     */
    private boolean linkDrug(String flowsheet, int demographic, String atcCode) {
        if (atcCode == null || atcCode.isBlank()) {
            return false;
        }
        boolean linked = flowSheetDrugDao.getFlowSheetDrugs(flowsheet, demographic).stream()
                .anyMatch(existing -> atcCode.equals(existing.getAtcCode()));
        if (!linked) {
            FlowSheetDrug cust = new FlowSheetDrug();
            cust.setFlowsheet(flowsheet);
            cust.setAtcCode(atcCode);
            cust.setProviderNo((String) request.getSession().getAttribute("user"));
            cust.setDemographicNo(demographic);
            cust.setCreateDate(new Date());
            flowSheetDrugDao.persist(cust);
        }
        return true;
    }

    /**
     * Links the diagnosis with this code and coding system to the flowsheet, unless the flowsheet already links it
     * for the patient.
     *
     * @return true when a link exists afterwards (saved now or before); false for a blank code or coding system
     */
    private boolean linkDx(String flowsheet, int demographic, String dxCode, String dxCodeType) {
        if (dxCode == null || dxCode.isBlank() || dxCodeType == null || dxCodeType.isBlank()) {
            return false;
        }
        // The map is keyed as the flowsheet page reads it: coding system then code.
        if (!flowSheetDxDao.getFlowSheetDxMap(flowsheet, demographic).containsKey(dxCodeType + dxCode)) {
            FlowSheetDx cust = new FlowSheetDx();
            cust.setFlowsheet(flowsheet);
            cust.setDxCode(dxCode);
            cust.setDxCodeType(dxCodeType);
            cust.setProviderNo((String) request.getSession().getAttribute("user"));
            cust.setDemographicNo(demographic);
            cust.setCreateDate(new Date());
            flowSheetDxDao.persist(cust);
        }
        return true;
    }
}
