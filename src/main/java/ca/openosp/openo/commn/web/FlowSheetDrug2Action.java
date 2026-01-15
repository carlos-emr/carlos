//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.commn.web;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.FlowSheetDrugDao;
import ca.openosp.openo.commn.dao.FlowSheetDxDao;
import ca.openosp.openo.commn.model.FlowSheetDrug;
import ca.openosp.openo.commn.model.FlowSheetDx;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger log2 = MiscUtils.getLogger();
    private FlowSheetDrugDao flowSheetDrugDao = SpringUtils.getBean(FlowSheetDrugDao.class);
    private FlowSheetDxDao flowSheetDxDao = SpringUtils.getBean(FlowSheetDxDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Main execution method that routes to appropriate sub-methods based on method parameter.
     * <p>
     * Routes to:
     * <ul>
     * <li>save() - Saves flowsheet-drug association</li>
     * <li>dxSave() - Saves flowsheet-diagnosis association</li>
     * </ul>
     *
     * Expected request parameters:
     * <ul>
     * <li>method - String method name ("save" or "dxSave")</li>
     * </ul>
     *
     * @return String result from routed method, or null if no method specified
     * @throws Exception if persistence or security check fails
     */
    @Override
    public String execute() throws Exception {
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }
        if ("dxSave".equals(request.getParameter("method"))) {
            return dxSave();
        }
        log2.debug("AnnotationAction-unspec");
        return null;
    }

    /**
     * Saves a flowsheet-drug association using ATC code.
     * <p>
     * Creates a new FlowSheetDrug record linking a flowsheet template to a specific
     * drug identified by its ATC (Anatomical Therapeutic Chemical) code for a patient.
     * Requires write privilege for the demographic.
     *
     * Expected request parameters:
     * <ul>
     * <li>flowsheet - String flowsheet template name</li>
     * <li>demographic - String demographic number</li>
     * <li>atcCode - String ATC code of the drug</li>
     * </ul>
     *
     * @return String JSP path with query parameters for redirect to flowsheet
     * @throws Exception if persistence fails
     * @throws SecurityException if user lacks write privilege for demographic
     */
    public String save() throws Exception {
        String flowsheet = request.getParameter("flowsheet");
        String demographicNo = request.getParameter("demographic");
        String atcCode = request.getParameter("atcCode");

        FlowSheetDrug cust = new FlowSheetDrug();
        cust.setFlowsheet(flowsheet);
        cust.setAtcCode(atcCode);
        cust.setProviderNo((String) request.getSession().getAttribute("user"));
        cust.setDemographicNo(Integer.parseInt(demographicNo));
        cust.setCreateDate(new Date());

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        log2.debug("SAVE " + cust);

        flowSheetDrugDao.persist(cust);

        return "/oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=" + demographicNo + "&template=" + flowsheet;
    }

    /**
     * Saves a flowsheet-diagnosis association using diagnosis code and type.
     * <p>
     * Creates a new FlowSheetDx record linking a flowsheet template to a specific
     * diagnosis identified by its code and type (e.g., ICD-9, ICD-10) for a patient.
     * Requires write privilege for the demographic.
     *
     * Expected request parameters:
     * <ul>
     * <li>flowsheet - String flowsheet template name</li>
     * <li>demographic - String demographic number</li>
     * <li>dxCode - String diagnosis code</li>
     * <li>dxCodeType - String diagnosis code type (e.g., "icd9", "icd10")</li>
     * </ul>
     *
     * @return String JSP path with query parameters for redirect to flowsheet
     * @throws Exception if persistence fails
     * @throws SecurityException if user lacks write privilege for demographic
     */
    public String dxSave() throws Exception {
        String flowsheet = request.getParameter("flowsheet");
        String demographicNo = request.getParameter("demographic");
        String dxCode = request.getParameter("dxCode");
        String dxType = request.getParameter("dxCodeType");

        FlowSheetDx cust = new FlowSheetDx();
        cust.setFlowsheet(flowsheet);
        cust.setDxCode(dxCode);
        cust.setDxCodeType(dxType);
        cust.setProviderNo((String) request.getSession().getAttribute("user"));
        cust.setDemographicNo(Integer.parseInt(demographicNo));
        cust.setCreateDate(new Date());

        log2.debug("SAVE " + cust);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        flowSheetDxDao.persist(cust);

        return "/oscarEncounter/oscarMeasurements/TemplateFlowSheet.jsp?demographic_no=" + demographicNo + "&template=" + flowsheet;
    }
}
