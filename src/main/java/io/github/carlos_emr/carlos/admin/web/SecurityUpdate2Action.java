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

import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePinHandler;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies security-record edits only against the original state, under a refreshed row lock.
 *
 * <p>Requires either {@code _admin} or {@code _admin.userAdmin} write privilege.
 * POST method is enforced; non-POST requests receive HTTP 405.
 * A confirmed transaction commit precedes success; refusals retain the submitted draft.</p>
 *
 * @since 2026-04-05
 */
public class SecurityUpdate2Action extends ActionSupport {

    private final transient CarlosMethodSecurity methodSecurity;

    public SecurityUpdate2Action() {
        this(SpringUtils.getBean(CarlosMethodSecurity.class));
    }

    @Autowired
    public SecurityUpdate2Action(CarlosMethodSecurity methodSecurity) {
        this.methodSecurity = methodSecurity;
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
        response.setHeader("Cache-Control", "no-store");

        Integer id;
        try {
            id = Integer.valueOf(request.getParameter("security_no"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return SUCCESS;
        }
        int[] completion = {TransactionSynchronization.STATUS_UNKNOWN};
        try {
            TransactionTemplate transaction = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class));
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String result = transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int value) { completion[0] = value; }
                });
                String outcome = update(request, response, id);
                if (!SUCCESS.equals(outcome)) status.setRollbackOnly();
                return outcome;
            });
            if (SUCCESS.equals(result)) {
                if (completion[0] != TransactionSynchronization.STATUS_COMMITTED) {
                    throw new IllegalStateException("Security edit did not confirm a commit");
                }
                request.setAttribute("securityUpdateCommitted", true);
            }
            return result;
        } catch (RuntimeException e) {
            // Exception messages can contain submitted credentials or database values.
            MiscUtils.getLogger().error("Failed to confirm security edit: {}", e.getClass().getSimpleName());
            return refuse(request, response, id, 500, "admin.securityupdate.msgUnconfirmed");
        }
    }

    private String update(HttpServletRequest request, HttpServletResponse response, int id) {
        SecurityDao dao = SpringUtils.getBean(SecurityDao.class);
        Security row = dao.findForUpdate(id);
        if (row == null) return refuse(request, response, id, 404, "admin.securityupdate.msgMissing");
        if (!SecurityEditVersion.of(row).equals(request.getParameter(SecurityEditVersion.PARAMETER))) {
            return refuse(request, response, id, 409, "admin.securityupdate.msgStale");
        }
        String userName = request.getParameter("user_name");
        if (userName == null || !userName.trim().matches("[a-zA-Z0-9]{1,30}")) {
            return refuse(request, response, id, 400, "admin.securityupdate.msgUserNameInvalid");
        }
        CarlosProperties properties = CarlosProperties.getInstance();
        String password = request.getParameter("password");
        String error = SecurityUpdatePasswordValidator.validate(password, request.getParameter("conPassword"), properties);
        if (error != null) return refuse(request, response, id, 400, error);
        String provider = request.getParameter("provider_no");
        if (provider == null || provider.isBlank()) {
            return refuse(request, response, id, 400, "admin.securityupdate.msgUpdateFailure");
        }
        String pin = request.getParameter("pin");
        if (pin != null && !pin.equals(request.getParameter("conPin"))) {
            return refuse(request, response, id, 400, "admin.securityrecord.msgPinNotConfirmed");
        }
        Date expiry;
        try {
            String raw = request.getParameter("date_ExpireDate");
            expiry = raw == null || raw.isBlank() ? null : java.sql.Date.valueOf(LocalDate.parse(raw));
            if (checked(request, "b_ExpireSet") && expiry == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return refuse(request, response, id, 400, "admin.securityupdate.msgUpdateFailure");
        }
        row.setUserName(userName.trim());
        row.setProviderNo(provider);
        row.setBExpireset(checked(request, "b_ExpireSet") ? 1 : 0);
        row.setDateExpiredate(expiry);
        if (!SecurityUpdatePasswordValidator.UNCHANGED_PASSWORD.equals(password)) {
            row.setPassword(SpringUtils.getBean(SecurityManager.class).encodePassword(password));
            row.setPasswordUpdateDate(new Date());
        }
        // Controls hidden by policy or disabled by MFA must not silently clear existing protection.
        if (!properties.getBooleanProperty("mandatory_password_reset", "false")
                && request.getParameter("forcePasswordReset") != null) {
            row.setForcePasswordReset(checked(request, "forcePasswordReset"));
        }
        if (MfaManager.isOscarMfaEnabled()) row.setUsingMfa(checked(request, "enableMfa"));
        if (MfaManager.isOscarLegacyPinEnabled() && !row.isUsingMfa()) {
            row.setBLocallockset(checked(request, "b_LocalLockSet") ? 1 : 0);
            row.setBRemotelockset(checked(request, "b_RemoteLockSet") ? 1 : 0);
            SecurityUpdatePinHandler.apply(row, pin, properties.isPINEncripted());
        }
        row.setLastUpdateDate(new Date());
        row.setLastUpdateUser((String) request.getSession().getAttribute("user"));
        dao.saveEntity(row);
        LogAction.addLogSynchronous((String) request.getSession().getAttribute("user"), LogConst.UPDATE, LogConst.CON_SECURITY,
                id + "->" + row.getUserName(), request.getRemoteAddr());
        return SUCCESS;
    }

    private static boolean checked(HttpServletRequest request, String name) {
        return "1".equals(request.getParameter(name));
    }

    private static String refuse(HttpServletRequest request, HttpServletResponse response, int id, int status, String key) {
        response.setStatus(status);
        Security draft = new Security();
        draft.setId(id);
        draft.setUserName(request.getParameter("user_name"));
        draft.setProviderNo(request.getParameter("provider_no"));
        draft.setBExpireset(checked(request, "b_ExpireSet") ? 1 : 0);
        draft.setBLocallockset(checked(request, "b_LocalLockSet") ? 1 : 0);
        draft.setBRemotelockset(checked(request, "b_RemoteLockSet") ? 1 : 0);
        draft.setForcePasswordReset(checked(request, "forcePasswordReset"));
        draft.setUsingMfa(checked(request, "enableMfa"));
        request.setAttribute("securityEditDraft", draft);
        request.setAttribute("securityEditErrorKey", key);
        request.setAttribute("securityReviewAvailable", status != 404);
        return "conflict";
    }
}
