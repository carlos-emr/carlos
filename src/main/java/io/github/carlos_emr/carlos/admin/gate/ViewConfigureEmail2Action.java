/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.gate;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.function.BooleanSupplier;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailFooterAuditService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PortalRequestPreparationException;
import org.springframework.beans.factory.BeanCreationException;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code admin/configureEmail.jsp}. Enforces {@code _admin}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/admin/} location. Part of the admin module
 * security-hardening migration (defense in depth; matches the 2Action
 * gate pattern from #1109, #1629, #1632, #1644, #1662, #1663).
 *
 * <p>The page also shows the clinic's email footer logo (issue #3981), which {@code _admin}
 * writers can replace or remove there.</p>
 *
 * @since 2026-04-13
 */
public final class ViewConfigureEmail2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterLogoService logoService;
    private final ClinicEmailFooterService clinicFooters;
    private final PortalEmailFooterAuditService portalFooterAudit;
    private final BooleanSupplier portalConfigured;

    /** Used by Struts, which needs a no-argument constructor. */
    public ViewConfigureEmail2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterLogoService.class),
                SpringUtils.getBean(ClinicEmailFooterService.class),
                new PortalEmailFooterAuditService(SpringUtils.getBean(SecurityInfoManager.class)));
    }

    // Package-private for tests.
    ViewConfigureEmail2Action(SecurityInfoManager securityInfoManager, EmailFooterLogoService logoService,
            ClinicEmailFooterService clinicFooters) {
        this(securityInfoManager, logoService, clinicFooters, null);
    }

    ViewConfigureEmail2Action(SecurityInfoManager securityInfoManager, EmailFooterLogoService logoService,
            ClinicEmailFooterService clinicFooters, PortalEmailFooterAuditService portalFooterAudit) {
        this(securityInfoManager, logoService, clinicFooters, portalFooterAudit, PatientPortalSettings::isConfigured);
    }

    ViewConfigureEmail2Action(SecurityInfoManager securityInfoManager, EmailFooterLogoService logoService,
            ClinicEmailFooterService clinicFooters, PortalEmailFooterAuditService portalFooterAudit,
            BooleanSupplier portalConfigured) {
        this.securityInfoManager = securityInfoManager;
        this.logoService = logoService;
        this.clinicFooters = clinicFooters;
        this.portalFooterAudit = portalFooterAudit;
        this.portalConfigured = portalConfigured;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String clinicFooter = clinicFooters.clinicFooter();
        request.setAttribute("clinicFooter", clinicFooter);
        request.setAttribute("clinicFooterMissing", clinicFooter.isEmpty());
        request.setAttribute("clinicFooterFingerprint",
                io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService.fingerprint(clinicFooter));
        EmailFooterLogo logo = logoService.currentLogo();
        request.setAttribute("clinicLogoSet", logo != null);
        if (logo != null) {
            request.setAttribute("clinicLogoWidth", logo.getWidth());
            request.setAttribute("clinicLogoHeight", logo.getHeight());
        }
        if (portalFooterAudit != null) loadPortalFooterAudit(request, loggedInInfo);
        return SUCCESS;
    }
    private void loadPortalFooterAudit(HttpServletRequest request, LoggedInInfo user) {
        try {
            if (!portalConfigured.getAsBoolean()) return;
            request.setAttribute("portalFooterAuditEnabled", true);
            String rawDate = singleParameter(request, "portalFooterDate");
            String before = singleParameter(request, "portalFooterBefore");
            LocalDate date = rawDate == null ? LocalDate.now(ZoneOffset.UTC) : parseDate(rawDate);
            if (before != null && !before.matches("[0-9]{20}-[0-9a-f]{32}")) {
                throw new IllegalArgumentException("invalid portal footer cursor");
            }
            request.setAttribute("portalFooterDate", date.toString());
            request.setAttribute("portalFooterAudit", portalFooterAudit.read(user, date, before));
        } catch (DateTimeParseException e) {
            request.setAttribute("portalFooterAuditError", "invalid");
        } catch (PatientPortalException | PatientPortalConfigurationException | PortalRequestPreparationException e) {
            request.setAttribute("portalFooterAuditEnabled", true);
            request.setAttribute("portalFooterAuditError", "unavailable");
        } catch (IllegalArgumentException e) {
            request.setAttribute("portalFooterAuditError", "invalid");
        } catch (BeanCreationException e) {
            if (!e.contains(PatientPortalConfigurationException.class)) throw e;
            request.setAttribute("portalFooterAuditEnabled", true);
            request.setAttribute("portalFooterAuditError", "unavailable");
        }
    }

    private static LocalDate parseDate(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("invalid portal footer date");
        }
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1) throw new IllegalArgumentException("invalid portal footer date");
        return date;
    }

    private static String singleParameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) return null;
        if (values.length != 1 || values[0] == null || values[0].isEmpty()) {
            throw new IllegalArgumentException("invalid portal footer query");
        }
        return values[0];
    }
}
