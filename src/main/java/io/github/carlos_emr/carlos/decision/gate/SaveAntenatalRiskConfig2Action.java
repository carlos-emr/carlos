/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute and/or modify it under GPL version 2 or later.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.decision.gate;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService;
import io.github.carlos_emr.carlos.decision.AntenatalRiskConfigService.InvalidConfigurationException;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * POST-only mutation endpoint for the shared antenatal risk configuration.
 *
 * <p>Editing a shared clinical-decision rule is more privileged than completing
 * a patient form. Callers therefore need both {@code _form w} and either
 * {@code _admin w} or {@code _admin.misc w}. Validation errors return the
 * submitted text to the editor without changing the current configuration.
 *
 * <p>A successful replacement is audited. This file drives risk prompts for every
 * antenatal chart, so "who changed it, and when" has to survive the change; the
 * document itself is not versioned, and the entry is written only after the
 * atomic store succeeds.
 *
 * @since 2026-08-11
 */
public final class SaveAntenatalRiskConfig2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    static final String SAVED_FLASH_ATTRIBUTE = "riskEditorSaved";

    private final SecurityInfoManager securityInfoManager;
    private final AntenatalRiskConfigService configService;

    /**
     * Creates the action as Struts instantiates it, one per request.
     *
     * <p>Struts constructs this class by name rather than as a Spring bean, so the
     * collaborators are resolved here. {@link AntenatalRiskConfigService} reads
     * {@code DOCUMENT_DIR} lazily, so a misconfigured install fails on save with a
     * displayable storage error rather than failing construction on every request.
     */
    public SaveAntenatalRiskConfig2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), new AntenatalRiskConfigService());
    }

    SaveAntenatalRiskConfig2Action(
            SecurityInfoManager securityInfoManager, AntenatalRiskConfigService configService) {
        this.securityInfoManager = securityInfoManager;
        this.configService = configService;
    }

    /**
     * Validates and stores a replacement antenatal risk-list document.
     *
     * <p>The HTTP method is checked before authorization and before any side effect,
     * so a non-POST request never reaches the privilege check or the audit write.
     *
     * @return {@link #NONE} after a 405 for any method other than POST;
     *         {@link #SUCCESS} after the document is stored and audited, which
     *         redirects back to the editor; {@link #INPUT} when the document is
     *         rejected or cannot be stored, re-rendering the editor with a
     *         displayable reason and the submitted text intact
     * @throws SecurityException when the caller lacks {@code _form w}, or lacks both
     *         {@code _admin w} and {@code _admin.misc w}
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        // Rejected with sendError + NONE rather than a "methodNotAllowed" result so
        // that MutatorActionGetRejectionContractUnitTest's discovery scan — which
        // keys on the SC_METHOD_NOT_ALLOWED reference — can see this mutator.
        //
        // Compared case-sensitively: RFC 9110 defines the method token as
        // case-sensitive, and equalsIgnoreCase here raises a SpotBugs
        // IMPROPER_UNICODE alert that would need a suppression to silence.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        requireWritePrivileges(loggedInInfo);

        String checklist = request.getParameter("checklist");
        try {
            configService.save(checklist);
            // Written synchronously: the audit executor drops queued tasks on
            // shutdown, and a shared clinical-configuration change is rare enough
            // that the durability is worth more than the async hand-off.
            LogAction.addLogSynchronous(loggedInInfo.getLoggedInProviderNo(), LogConst.UPDATE,
                    LogConst.CON_ANTENATAL_RISK_CONFIG, null, loggedInInfo.getIp());
            request.getSession().setAttribute(SAVED_FLASH_ATTRIBUTE, Boolean.TRUE);
            return SUCCESS;
        } catch (InvalidConfigurationException e) {
            request.setAttribute("riskEditorError", e.getMessage());
            request.setAttribute("riskEditorChecklist", checklist == null ? "" : checklist);
            return INPUT;
        } catch (IOException e) {
            logger.error("Failed to atomically store the antenatal risk configuration", e);
            request.setAttribute("riskEditorError", "The risk list could not be saved; the existing configuration was not changed.");
            request.setAttribute("riskEditorChecklist", checklist == null ? "" : checklist);
            return INPUT;
        }
    }

    private void requireWritePrivileges(LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form w)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "w", null)) {
            throw new SecurityException("missing required sec object (_admin w or _admin.misc w)");
        }
    }
}
