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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.admin.service.SecurityRecordUpdateCommand;
import io.github.carlos_emr.carlos.admin.service.SecurityRecordUpdateService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.www.admin.SecurityEditVersion;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Saves the Security Records edit form (Administration, Search/Edit/Delete Security Records,
 * Update Record) and forwards to {@code securityupdate.jsp}, which only reports the outcome.
 *
 * <p>Requires either {@code _admin} or {@code _admin.userAdmin} write privilege.
 * POST method is enforced; non-POST requests receive HTTP 405.</p>
 *
 * <p>The user name and password are validated first; a refused value writes nothing and shows its
 * message. The write itself is {@link SecurityRecordUpdateService#update}, which locks the login,
 * so the comparison with the version the form was rendered from
 * ({@link SecurityEditVersion#PARAMETER}) and the write cannot be separated by another save. A
 * form opened before another save changed the login is refused with HTTP 409 and the
 * {@value #OUTCOME_EDIT_CONFLICT} outcome, and nothing of it is written; a post without a version
 * is saved as before. The page never echoes a submitted credential.</p>
 *
 * @since 2026-04-05
 */
public class SecurityUpdate2Action extends ActionSupport {

    /** Request attribute naming the outcome {@code securityupdate.jsp} reports (one of the {@code OUTCOME_*} values). */
    public static final String OUTCOME_ATTRIBUTE = "securityUpdateOutcome";
    /** Request attribute: the message key of a refused password, set with {@link #OUTCOME_PASSWORD_INVALID}. */
    public static final String PASSWORD_ERROR_ATTRIBUTE = "securityUpdatePasswordError";

    public static final String OUTCOME_SAVED = "saved";
    public static final String OUTCOME_USER_NAME_INVALID = "userNameInvalid";
    public static final String OUTCOME_PASSWORD_INVALID = "passwordInvalid";
    public static final String OUTCOME_FAILED = "failed";
    /** The form was rendered before another save changed the login; answered with HTTP 409. */
    public static final String OUTCOME_EDIT_CONFLICT = "editConflict";
    /** Request attribute: the {@code security_no} the conflict page offers to reopen. */
    public static final String CONFLICT_NO_ATTRIBUTE = "securityUpdateConflictNo";

    private static final String USER_NAME_PATTERN = "[a-zA-Z0-9]{1,30}";
    private static final Logger logger = MiscUtils.getLogger();

    private final transient CarlosMethodSecurity methodSecurity;
    private final transient SecurityRecordUpdateService securityRecordUpdateService;

    /** Struts creates this action through its no-argument constructor when Spring does not. */
    public SecurityUpdate2Action() {
        this(SpringUtils.getBean(CarlosMethodSecurity.class), SpringUtils.getBean(SecurityRecordUpdateService.class));
    }

    /**
     * @param methodSecurity the shared admin write policy
     * @param securityRecordUpdateService the locked write of the form
     */
    @Autowired
    public SecurityUpdate2Action(CarlosMethodSecurity methodSecurity, SecurityRecordUpdateService securityRecordUpdateService) {
        this.methodSecurity = methodSecurity;
        this.securityRecordUpdateService = securityRecordUpdateService;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!methodSecurity.hasAdminWrite()) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        // Must stay aligned with the Login2Action username pattern and security.user_name
        // varchar(30): the form maxlength is browser-side only, so a direct POST outside
        // this charset or length must be rejected before the save (an over-length value
        // fails as a DB truncation error page; other charsets are rejected at login).
        String rawUserName = request.getParameter("user_name");
        String userName = rawUserName == null ? "" : rawUserName.trim();
        if (!userName.matches(USER_NAME_PATTERN)) {
            return outcome(request, OUTCOME_USER_NAME_INVALID);
        }
        // Validate before changing any field: a rejected password must not partially apply a
        // rename, PIN, or account-flag edit.
        String password = request.getParameter("password");
        String passwordError = SecurityUpdatePasswordValidator.validate(password,
                request.getParameter("conPassword"), CarlosProperties.getInstance());
        if (passwordError != null) {
            request.setAttribute(PASSWORD_ERROR_ATTRIBUTE, passwordError);
            return outcome(request, OUTCOME_PASSWORD_INVALID);
        }
        Integer securityNo = securityNo(request.getParameter("security_no"));
        if (securityNo == null) {
            return outcome(request, OUTCOME_FAILED);
        }

        String expireSet = request.getParameter("b_ExpireSet");
        String renderedVersion = request.getParameter(SecurityEditVersion.PARAMETER);
        SecurityRecordUpdateCommand edit = new SecurityRecordUpdateCommand(
                securityNo,
                userName,
                request.getParameter("provider_no"),
                expireSet == null ? 0 : Integer.parseInt(expireSet),
                request.getParameter("date_ExpireDate"),
                // Lock flags change only when the form says the checkboxes were editable; they are
                // disabled while MFA is ticked and absent without the legacy PIN section
                // (finding 62 / #4503).
                "1".equals(request.getParameter("lockFlagsEditable")),
                request.getParameter("b_LocalLockSet"),
                request.getParameter("b_RemoteLockSet"),
                password,
                request.getParameter("pin"),
                "1".equals(request.getParameter("forcePasswordReset")),
                "1".equals(request.getParameter("enableMfa")),
                SecurityEditVersion.isPresent(renderedVersion) ? renderedVersion.trim() : null);

        switch (securityRecordUpdateService.update(edit)) {
            case SAVED -> {
                LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.UPDATE, LogConst.CON_SECURITY, // nosemgrep: java.servlets.security.tainted-session-from-http-request.tainted-session-from-http-request -- the session is only read (literal key "user"); the request values go to the OscarLog audit row through LogAction.addLog, and nothing here writes to the session
                        request.getParameter("security_no") + "->" + rawUserName, request.getRemoteAddr());
                return outcome(request, OUTCOME_SAVED);
            }
            case ALREADY_STORED -> {
                // The login already holds what this stale form posts (most often the second click
                // of a form whose first click saved; the browser shows this later response). Nothing
                // was written, so no audit row; the page reports the save the first click made.
                return outcome(request, OUTCOME_SAVED);
            }
            case STALE -> {
                // Another save changed the login after this form was opened: refused whole, before
                // anything (password and PIN included) was written. Only the login number is passed
                // on, for the page's offer to reopen the record as it is now.
                logger.info("Refused a stale Security Records edit: securityNo={}", LogSafe.sanitizeObject(securityNo));
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setHeader("Cache-Control", "no-store");
                request.setAttribute(CONFLICT_NO_ATTRIBUTE, String.valueOf(securityNo));
                return outcome(request, OUTCOME_EDIT_CONFLICT);
            }
            default -> {
                return outcome(request, OUTCOME_FAILED);
            }
        }
    }

    private static String outcome(HttpServletRequest request, String outcome) {
        request.setAttribute(OUTCOME_ATTRIBUTE, outcome);
        return SUCCESS;
    }

    /** The posted {@code security_no}, or {@code null} when it names no possible login. */
    private static Integer securityNo(String value) {
        if (value == null || !value.trim().matches("\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(value.trim());
    }
}
