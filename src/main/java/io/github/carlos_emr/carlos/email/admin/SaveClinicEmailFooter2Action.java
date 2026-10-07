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
package io.github.carlos_emr.carlos.email.admin;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Saves the clinic's default email footer (Administration, Emails, Configure Email; issue #4093,
 * follow-up to #3981).
 *
 * <p>POST only, with {@code _admin} write; GET and HEAD get 405 before anything is read or saved.
 * Saving also applies the new default to users' own footers (see
 * {@link EmailFooterService#saveClinicDefault}); the users concerned are told on their next email,
 * so the administrator is not asked to confirm.</p>
 *
 * <p>The form sends back the fingerprint of the footer it showed, so an unedited save changes
 * nothing, and a save made after someone else changed the footer is shown again with the current
 * footer instead of overwriting it. A footer over the limit, or a save that collided with another,
 * is shown again for editing. The audit entry records who changed the default and how many users
 * were told, not the text.</p>
 *
 * @since 2026-10-07
 */
public final class SaveClinicEmailFooter2Action extends ActionSupport {

    static final String FOOTER_PARAM = "clinicFooter";
    static final String FINGERPRINT_PARAM = "clinicFooterFingerprint";

    private static final Logger logger = MiscUtils.getLogger();
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterService emailFooterService;

    public SaveClinicEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class));
    }

    // Package-private so tests can supply the collaborators.
    SaveClinicEmailFooter2Action(SecurityInfoManager securityInfoManager, EmailFooterService emailFooterService) {
        this.securityInfoManager = securityInfoManager;
        this.emailFooterService = emailFooterService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        // HTTP method names are case-sensitive and upper case.
        if (!"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String footer = request.getParameter(FOOTER_PARAM);
        String shown = request.getParameter(FINGERPRINT_PARAM);
        if (footer == null || shown == null || !FINGERPRINT.matcher(shown).matches()) {
            // A post without the field is not a request to clear the footer, and one without the
            // fingerprint of what the page showed cannot be checked against later changes.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        EmailFooterService.ClinicDefaultSaved saved;
        try {
            saved = emailFooterService.saveClinicDefault(footer, shown);
        } catch (EmailFooterService.FooterTooLongException e) {
            return showAgain(request, footer, shown, "clinicFooterTooLong");
        } catch (ConcurrencyFailureException e) {
            // Another save of the footers ran at the same moment; this one was rolled back whole.
            logger.warn("Clinic email footer save collided with another save; asked the user to retry ({})",
                    e.getClass().getSimpleName());
            return showAgain(request, footer, shown, "clinicFooterSaveConflict");
        }
        if (saved.outcome() == EmailFooterService.ClinicDefaultOutcome.CHANGED_SINCE_SHOWN) {
            String current = emailFooterService.clinicDefault();
            request.setAttribute("clinicFooterCurrent", current);
            // The page now shows the current footer, so a second save goes through.
            return showAgain(request, footer, EmailFooterService.fingerprint(current), "clinicFooterChangedSinceShown");
        }
        if (!saved.changed()) {
            // Nothing to audit. Not "saved" either: from a stale page, the footer now shown is
            // someone else's, and the administrator should see that nothing they typed was stored.
            response.sendRedirect(request.getContextPath() + "/admin/ViewConfigureEmail?clinicFooterUnchanged=true");
            return NONE;
        }
        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), LogConst.UPDATE, "emailFooterClinicDefault", "",
                request.getRemoteAddr(), null, "noticed=" + saved.noticed());
        response.sendRedirect(request.getContextPath() + "/admin/ViewConfigureEmail?clinicFooterSaved=true");
        return NONE;
    }

    /** Shows the page again with the administrator's text in the box and the given message. */
    private String showAgain(HttpServletRequest request, String footer, String fingerprint, String message) {
        request.setAttribute(FOOTER_PARAM, footer);
        request.setAttribute(FINGERPRINT_PARAM, fingerprint);
        request.setAttribute("ownFootersReplacedOnClinicChange", emailFooterService.ownFootersReplacedOnClinicChange());
        request.setAttribute(message, true);
        return INPUT;
    }
}
