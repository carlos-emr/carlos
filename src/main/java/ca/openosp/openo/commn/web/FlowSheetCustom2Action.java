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

import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import ca.openosp.openo.commn.dao.FlowSheetCustomizationDao;
import ca.openosp.openo.commn.dao.FlowSheetUserCreatedDao;
import ca.openosp.openo.commn.model.FlowSheetCustomization;
import ca.openosp.openo.commn.model.FlowSheetUserCreated;
import ca.openosp.openo.commn.service.FlowSheetCustomizationService;
import ca.openosp.openo.commn.service.FlowSheetCustomizationService.CascadeCheckResult;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.encounter.oscarMeasurements.FlowSheetItem;
import ca.openosp.openo.encounter.oscarMeasurements.MeasurementFlowSheet;
import ca.openosp.openo.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import ca.openosp.openo.encounter.oscarMeasurements.util.Recommendation;
import ca.openosp.openo.encounter.oscarMeasurements.util.RecommendationCondition;
import ca.openosp.openo.encounter.oscarMeasurements.util.TargetColour;
import ca.openosp.openo.encounter.oscarMeasurements.util.TargetCondition;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import java.util.*;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class FlowSheetCustom2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger logger = MiscUtils.getLogger();

    private FlowSheetCustomizationDao flowSheetCustomizationDao = SpringUtils.getBean(FlowSheetCustomizationDao.class);
    private FlowSheetUserCreatedDao flowSheetUserCreatedDao = SpringUtils.getBean(FlowSheetUserCreatedDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private FlowSheetCustomizationService flowSheetCustomizationService = SpringUtils.getBean(FlowSheetCustomizationService.class);

    /**
     * Helper class to hold scope context for flowsheet operations.
     */
    private static class ScopeContext {
        final String flowsheet;
        final String measurement;
        final String demographicNo;
        final String scope;
        final String providerNo;
        final boolean isClinicScope;
        final boolean isPatientScope;

        ScopeContext(String flowsheet, String measurement, String demographicNo, String scope, String providerNo) {
            this.flowsheet = flowsheet;
            this.measurement = measurement;
            this.demographicNo = demographicNo;
            this.scope = scope;
            this.providerNo = providerNo;
            this.isClinicScope = "clinic".equals(scope);
            this.isPatientScope = demographicNo != null && !"0".equals(demographicNo);
        }

        /**
         * Returns the provider number to use for cascade checks.
         * Always uses the logged-in provider to check for provider-level blocking.
         */
        String cascadeCheckProviderNo(LoggedInInfo loggedInInfo) {
            return loggedInInfo.getLoggedInProviderNo();
        }
    }

    /**
     * Create a ScopeContext object from request parameters.
     *
     * @return ScopeContext with resolved values
     */
    private ScopeContext parseScopeContext() {
        String flowsheet = request.getParameter("flowsheet");
        String measurement = request.getParameter("measurement");
        String demographicNoParam = request.getParameter("demographic");
        String scope = request.getParameter("scope");
        String demographicNo;
        String providerNo;
        boolean isClinicScope = "clinic".equals(scope);
        boolean isPatientScope = !isClinicScope && demographicNoParam != null && !"0".equals(demographicNoParam);

        if (isClinicScope) {
            demographicNo = "0";
            providerNo = "";
        } else if (isPatientScope) {
            demographicNo = demographicNoParam;
            providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        } else {
            demographicNo = "0";
            providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        }

        return new ScopeContext(flowsheet, measurement, demographicNo, scope, providerNo);
    }

    /**
     * Sets standard response attributes after a flowsheet operation.
     *
     * @param ctx the scope context containing flowsheet and demographic info
     */
    private void setResponseAttributes(ScopeContext ctx) {
        request.setAttribute("demographic", ctx.demographicNo);
        request.setAttribute("flowsheet", ctx.flowsheet);
    }

    /**
     * Validates all customization permissions for the given scope and demographic.
     * Checks: demographic access and scope permission (admin for clinic).
     *
     * @param scope the scope ("clinic", or null for provider/patient)
     * @param demographicNo the demographic number ("0" for clinic/provider, patient ID otherwise)
     * @return LoggedInInfo for the current user
     * @throws SecurityException if any permission check fails
     */
    private LoggedInInfo validateCustomizationPermissions(String scope, String demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        flowSheetCustomizationService.validateScopePermission(loggedInInfo, scope);

        return loggedInInfo;
    }

    /**
     * Handles cascade blocking results by setting error attributes and logging.
     *
     * @param result the cascade check result
     * @param action description of the blocked action (e.g., "hide", "add", "restore")
     * @param measurement the measurement name for logging
     * @param ctx the scope context for setting response attributes
     * @return true if blocked (caller should return ERROR), false if allowed
     */
    private boolean handleCascadeBlocked(CascadeCheckResult result, String action, String measurement, ScopeContext ctx) {
        if (!result.isBlocked()) {
            return false;
        }
        logger.warn("Cannot {} measurement {} - blocked at {} level", action, measurement, result.getBlockingLevel());
        request.setAttribute("errorMessage",
            "Cannot " + action + " measurement: blocked at " + result.getBlockingLevel() + " level");
        setResponseAttributes(ctx);
        return true;
    }

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("save".equals(method)) {
            return save();
        } else if ("update".equals(method)) {
            return update();
        } else if ("hide".equals(method)) {
            return hide();
        } else if ("restore".equals(method)) {
            return restore();
        } else if ("archiveMod".equals(method)) {
            return archiveMod();
        } else if ("createNewFlowSheet".equals(method)) {
            return createNewFlowSheet();
        } else if ("revertUpdate".equals(method)) {
            return revertUpdate();
        }
        return SUCCESS;
    }

    public String save() throws Exception {
        String flowsheet = request.getParameter("flowsheet");
        String demographicNo = Optional.ofNullable(request.getParameter("demographic")).orElse("0");
        String scope = request.getParameter("scope");

        LoggedInInfo loggedInInfo = validateCustomizationPermissions(scope, demographicNo);

        MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();
        MeasurementFlowSheet mFlowsheet = templateConfig.getFlowSheet(flowsheet);

        if (request.getParameter("measurement") != null) {

            Hashtable<String, String> h = new Hashtable<String, String>();

            h.put("measurement_type", request.getParameter("measurement"));
            h.put("display_name", request.getParameter("display_name"));
            h.put("guideline", request.getParameter("guideline"));
            h.put("graphable", request.getParameter("graphable"));
            h.put("value_name", request.getParameter("value_name"));
            String prevItem = null;
            if (request.getParameter("count") != null) {
                int cou = Integer.parseInt(request.getParameter("count"));
                if (cou != 0) {
                    prevItem = mFlowsheet.getMeasurementList().get(cou);
                }
            }

            @SuppressWarnings("unchecked")
            Enumeration<String> en = request.getParameterNames();

            List<Recommendation> ds = new ArrayList<Recommendation>();
            while (en.hasMoreElements()) {
                String s = en.nextElement();
                if (s.startsWith("monthrange")) {
                    String extrachar = s.replaceAll("monthrange", "").trim();
                    logger.debug("EXTRA CAH " + extrachar);

                    if (request.getParameter("monthrange" + extrachar) != null) {
                        String mRange = request.getParameter("monthrange" + extrachar);
                        String strn = request.getParameter("strength" + extrachar);
                        String dsText = request.getParameter("text" + extrachar);

                        if (!mRange.trim().equals("")) {
                            ds.add(new Recommendation("" + h.get("measurement_type"), mRange, strn, dsText));
                        }
                    }
                }
            }

            if (h.get("measurement_type") != null) {
                String measurementType = h.get("measurement_type");
                String providerNo = "clinic".equals(scope) ? "" : loggedInInfo.getLoggedInProviderNo();

                // Check for blocking customization from higher level
                CascadeCheckResult cascadeResult = flowSheetCustomizationService.checkCascadingBlocked(
                    flowsheet, measurementType, FlowSheetCustomization.ADD,
                    loggedInInfo.getLoggedInProviderNo(), demographicNo);

                if (cascadeResult.isBlocked()) {
                    logger.warn("Cannot add measurement {} - blocked at {} level",
                        measurementType, cascadeResult.getBlockingLevel());
                    request.setAttribute("errorMessage",
                        "Cannot add measurement: blocked at " + cascadeResult.getBlockingLevel() + " level");
                    request.setAttribute("demographic", demographicNo);
                    request.setAttribute("flowsheet", flowsheet);
                    return ERROR;
                }

                FlowSheetItem item = new FlowSheetItem(h);
                item.setRecommendations(ds);
                Element va = templateConfig.getItemFromObject(item);

                XMLOutputter outp = new XMLOutputter();
                outp.setFormat(Format.getPrettyFormat());

                FlowSheetCustomization cust = new FlowSheetCustomization();
                cust.setAction(FlowSheetCustomization.ADD);
                cust.setPayload(outp.outputString(va));
                cust.setFlowsheet(flowsheet);
                cust.setMeasurement(prevItem);//THIS THE MEASUREMENT TO SET THIS AFTER!
                cust.setProviderNo(providerNo);
                cust.setDemographicNo(demographicNo);
                cust.setCreateDate(new Date());

                logger.debug("SAVE " + cust);

                flowSheetCustomizationDao.persist(cust);

            }
        }
        request.setAttribute("demographic", demographicNo);
        request.setAttribute("flowsheet", flowsheet);
        return SUCCESS;
    }

    public String update() {
        MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();

        String flowsheet = request.getParameter("flowsheet");
        String demographicNo = Optional.ofNullable(request.getParameter("demographic")).orElse("0");
        String scope = request.getParameter("scope");

        LoggedInInfo loggedInInfo = validateCustomizationPermissions(scope, demographicNo);

        logger.debug("UPDATING FOR demographic " + demographicNo);

        if (request.getParameter("updater") != null) {
            Hashtable<String, String> h = new Hashtable<String, String>();
            h.put("measurement_type", request.getParameter("measurement_type"));
            h.put("display_name", request.getParameter("display_name"));
            h.put("guideline", request.getParameter("guideline"));
            h.put("graphable", request.getParameter("graphable"));
            h.put("value_name", request.getParameter("value_name"));

            String providerNo = "clinic".equals(scope) ? "" : loggedInInfo.getLoggedInProviderNo();

            // UPDATE customizations are allowed at any level - no cascade blocking
            // Users can revert to higher-scope settings using the Revert button

            FlowSheetItem item = new FlowSheetItem(h);


            @SuppressWarnings("unchecked")
            Enumeration<String> en = request.getParameterNames();

            List<TargetColour> targets = new ArrayList<TargetColour>();
            List<Recommendation> recommendations = new ArrayList<Recommendation>();
            while (en.hasMoreElements()) {
                String s = en.nextElement();
                if (s.startsWith("strength")) {
                    String extrachar = s.replaceAll("strength", "").trim();
                    logger.debug("EXTRA CAH " + extrachar);
                    boolean go = true;
                    Recommendation rec = new Recommendation();
                    rec.setStrength(request.getParameter(s));
                    int targetCount = 1;
                    rec.setText(request.getParameter("text" + extrachar));
                    List<RecommendationCondition> conds = new ArrayList<RecommendationCondition>();
                    while (go) {
                        String type = request.getParameter("type" + extrachar + "c" + targetCount);
                        if (type != null) {
                            if (!type.equals("-1")) {
                                String param = request.getParameter("param" + extrachar + "c" + targetCount);
                                String value = request.getParameter("value" + extrachar + "c" + targetCount);
                                RecommendationCondition cond = new RecommendationCondition();
                                cond.setType(type);
                                cond.setParam(param);
                                cond.setValue(value);
                                if (value != null && !value.trim().equals("")) {
                                    conds.add(cond);
                                }
                            }
                        } else {
                            go = false;
                        }
                        targetCount++;
                    }
                    if (conds.size() > 0) {
                        rec.setRecommendationCondition(conds);
                        recommendations.add(rec);
                    }
                } else if (s.startsWith("col")) {
                    String extrachar = s.replaceAll("col", "").trim();
                    logger.debug("EXTRA CHA " + extrachar);
                    boolean go = true;
                    int targetCount = 1;
                    TargetColour tcolour = new TargetColour();
                    tcolour.setIndicationColor(request.getParameter(s));
                    List<TargetCondition> conds = new ArrayList<TargetCondition>();
                    while (go) {
                        String type = request.getParameter("targettype" + extrachar + "c" + targetCount);
                        if (type != null) {
                            if (!type.equals("-1")) {
                                String param = request.getParameter("targetparam" + extrachar + "c" + targetCount);
                                String value = request.getParameter("targetvalue" + extrachar + "c" + targetCount);
                                TargetCondition cond = new TargetCondition();
                                cond.setType(type);
                                cond.setParam(param);
                                cond.setValue(value);
                                if (value != null && !value.trim().equals("")) {
                                    conds.add(cond);
                                }
                            }
                        } else {
                            go = false;
                        }
                        targetCount++;
                    }
                    if (conds.size() > 0) {
                        tcolour.setTargetConditions(conds);
                        targets.add(tcolour);
                    }
                }
            }
            item.setTargetColour(targets);
            item.setRecommendations(recommendations);

            Element va = templateConfig.getItemFromObject(item);

            XMLOutputter outp = new XMLOutputter();
            outp.setFormat(Format.getPrettyFormat());

            FlowSheetCustomization cust = new FlowSheetCustomization();
            cust.setAction(FlowSheetCustomization.UPDATE);
            cust.setPayload(outp.outputString(va));
            cust.setFlowsheet(flowsheet);
            if (demographicNo != null) {
                cust.setDemographicNo(demographicNo);
            }
            cust.setMeasurement(item.getItemName());//THIS THE MEASUREMENT TO SET THIS AFTER!
            cust.setProviderNo(providerNo);

            logger.debug("UPDATE " + cust);

            flowSheetCustomizationDao.persist(cust);

        }
        request.setAttribute("demographic", demographicNo);
        request.setAttribute("flowsheet", flowsheet);
        return SUCCESS;
    }

    public String hide() {
        logger.debug("IN HIDE");
        ScopeContext ctx = parseScopeContext();
        LoggedInInfo loggedInInfo = validateCustomizationPermissions(ctx.scope, ctx.demographicNo);

        // Check for blocking customization from higher level
        CascadeCheckResult cascadeResult = flowSheetCustomizationService.checkCascadingBlocked(
            ctx.flowsheet, ctx.measurement, FlowSheetCustomization.DELETE,
            ctx.cascadeCheckProviderNo(loggedInInfo), ctx.demographicNo);

        if (handleCascadeBlocked(cascadeResult, "hide", ctx.measurement, ctx)) {
            return ERROR;
        }

        FlowSheetCustomization cust = new FlowSheetCustomization();
        cust.setAction(FlowSheetCustomization.DELETE);
        cust.setFlowsheet(ctx.flowsheet);
        cust.setMeasurement(ctx.measurement);
        cust.setProviderNo(ctx.providerNo);
        cust.setDemographicNo(ctx.demographicNo);

        flowSheetCustomizationDao.persist(cust);
        logger.debug("HIDE " + cust);

        setResponseAttributes(ctx);
        return SUCCESS;
    }

    public String restore() {
        logger.debug("IN RESTORE");
        ScopeContext ctx = parseScopeContext();
        LoggedInInfo loggedInInfo = validateCustomizationPermissions(ctx.scope, ctx.demographicNo);

        // Check for blocking hide from higher level - cannot restore if hidden at higher level
        CascadeCheckResult cascadeResult = flowSheetCustomizationService.checkCascadingBlocked(
            ctx.flowsheet, ctx.measurement, FlowSheetCustomization.DELETE,
            ctx.cascadeCheckProviderNo(loggedInInfo), ctx.demographicNo);

        if (handleCascadeBlocked(cascadeResult, "restore", ctx.measurement, ctx)) {
            return ERROR;
        }

        List<FlowSheetCustomization> customizations;
        if (ctx.isClinicScope) {
            // Clinic level
            customizations = flowSheetCustomizationDao.getClinicLevelCustomizations(ctx.flowsheet);
        } else if (ctx.isPatientScope) {
            // Patient level
            customizations = flowSheetCustomizationDao.getPatientLevelCustomizations(ctx.flowsheet, ctx.demographicNo);
        } else {
            // Provider level
            customizations = flowSheetCustomizationDao.getProviderLevelCustomizations(ctx.flowsheet, ctx.providerNo);
        }

        for (FlowSheetCustomization cust : customizations) {
            if (FlowSheetCustomization.DELETE.equals(cust.getAction()) && ctx.measurement.equals(cust.getMeasurement())) {
                flowSheetCustomizationDao.remove(cust.getId());
            }
        }

        setResponseAttributes(ctx);
        return SUCCESS;
    }

    /**
     * Reverts an UPDATE customization at the current scope level.
     * After reversion, the measurement settings fall back to the next highest
     * scope's customization (or base flowsheet settings if none).
     *
     * @return SUCCESS after archiving the customization
     */
    public String revertUpdate() {
        logger.debug("IN REVERT_UPDATE");
        ScopeContext ctx = parseScopeContext();
        validateCustomizationPermissions(ctx.scope, ctx.demographicNo);

        // Get customizations at CURRENT scope level only
        List<FlowSheetCustomization> customizations;
        if (ctx.isClinicScope) {
            customizations = flowSheetCustomizationDao.getClinicLevelCustomizations(ctx.flowsheet);
        } else if (ctx.isPatientScope) {
            customizations = flowSheetCustomizationDao.getPatientLevelCustomizations(
                ctx.flowsheet, ctx.demographicNo);
        } else {
            customizations = flowSheetCustomizationDao.getProviderLevelCustomizations(
                ctx.flowsheet, ctx.providerNo);
        }

        // Archive UPDATE customization for this measurement
        for (FlowSheetCustomization cust : customizations) {
            if (FlowSheetCustomization.UPDATE.equals(cust.getAction()) &&
                ctx.measurement.equals(cust.getMeasurement())) {
                cust.setArchived(true);
                cust.setArchivedDate(new Date());
                flowSheetCustomizationDao.merge(cust);
                logger.info("Reverted UPDATE customization {} for measurement {}",
                    cust.getId(), ctx.measurement);
            }
        }

        setResponseAttributes(ctx);
        return SUCCESS;
    }

    public String archiveMod() {
        logger.debug("IN MOD");
        String id = request.getParameter("id");
        String flowsheet = request.getParameter("flowsheet");
        String demographicNo = request.getParameter("demographic");
        String scope = request.getParameter("scope");

        LoggedInInfo loggedInInfo = validateCustomizationPermissions(scope, demographicNo);

        FlowSheetCustomization cust = flowSheetCustomizationDao.getFlowSheetCustomization(Integer.parseInt(id));
        if (cust != null) {
            String currentProviderNo = "clinic".equals(scope) ? "" : loggedInInfo.getLoggedInProviderNo();

            // Check if trying to archive a customization from a higher scope
            CascadeCheckResult canArchive = flowSheetCustomizationService.checkCanArchive(
                cust, scope, currentProviderNo, demographicNo);

            if (canArchive.isBlocked()) {
                logger.warn("Cannot archive customization {} - created at {} level",
                    id, canArchive.getBlockingLevel());
                request.setAttribute("errorMessage",
                    "Cannot remove customization: created at " + canArchive.getBlockingLevel() + " level");
                request.setAttribute("demographic", demographicNo);
                request.setAttribute("flowsheet", flowsheet);
                return ERROR;
            }

            cust.setArchived(true);
            cust.setArchivedDate(new Date());
            flowSheetCustomizationDao.merge(cust);
        }
        logger.debug("archiveMod " + cust);

        request.setAttribute("demographic", demographicNo);
        request.setAttribute("flowsheet", flowsheet);
        return SUCCESS;
    }

    /*first add it as a flowsheet into the current system.  The save it to the database so that it will be there on reboot */
    public String createNewFlowSheet() {
        logger.debug("IN create new flowsheet");
        //String name let oscar create the name
        String dxcodeTriggers = request.getParameter("dxcodeTriggers");
        String displayName = request.getParameter("displayName");
        String warningColour = request.getParameter("warningColour");
        String recommendationColour = request.getParameter("recommendationColour");
        //String topHTML 				= request.getParameter("topHTML");  // Not supported yet


        /// NEW FLOWSHEET CODE
        MeasurementFlowSheet m = new MeasurementFlowSheet();
        m.parseDxTriggers(dxcodeTriggers);
        m.setDisplayName(displayName);
        m.setWarningColour(warningColour);
        m.setRecommendationColour(recommendationColour);

        MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();
        templateConfig.addIndicatorsInCustomFlowsheet(m);
        String name = templateConfig.addFlowsheet(m);
        m.loadRuleBase();
        /// END FLOWSHEET CODE

        FlowSheetUserCreated fsuc = new FlowSheetUserCreated();
        fsuc.setName(name);
        fsuc.setDisplayName(displayName);
        fsuc.setDxcodeTriggers(dxcodeTriggers);
        fsuc.setWarningColour(warningColour);
        fsuc.setRecommendationColour(recommendationColour);
        fsuc.setArchived(false);
        fsuc.setCreatedDate(new Date());
        flowSheetUserCreatedDao.persist(fsuc);

        request.setAttribute("flowsheet", fsuc.getName());
        request.setAttribute("displayName", fsuc.getDisplayName());
        return SUCCESS;
    }
}
