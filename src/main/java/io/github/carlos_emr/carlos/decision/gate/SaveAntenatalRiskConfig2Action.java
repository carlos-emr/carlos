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
 * @since 2026-08-11
 */
public final class SaveAntenatalRiskConfig2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    static final String SAVED_FLASH_ATTRIBUTE = "riskEditorSaved";

    private final SecurityInfoManager securityInfoManager;
    private final AntenatalRiskConfigService configService;

    public SaveAntenatalRiskConfig2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), new AntenatalRiskConfigService());
    }

    SaveAntenatalRiskConfig2Action(
            SecurityInfoManager securityInfoManager, AntenatalRiskConfigService configService) {
        this.securityInfoManager = securityInfoManager;
        this.configService = configService;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return "methodNotAllowed";
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        requireWritePrivileges(loggedInInfo);

        String checklist = request.getParameter("checklist");
        try {
            configService.save(checklist);
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
