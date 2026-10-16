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
package io.github.carlos_emr.carlos.www.admin;

import java.util.Date;
import java.util.List;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PasswordPolicy;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.jsp.PageContext;

import io.github.carlos_emr.carlos.admin.service.ProviderLoginCreationService;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;


/**
 * Helper class for securityaddsecurity.jsp page.
 *
 * <p>Validates the Add a Login Record form and creates the login with the first role the
 * administrator chose (issue #2689). A login is never created without a role. The provider and
 * the role are checked after the existing credential and uniqueness refusals, so those keep
 * their messages; each must be one the form offers the acting administrator.</p>
 */
public class SecurityAddSecurityHelper {

    /** Page attribute naming the role given to a login that was created. */
    public static final String ASSIGNED_ROLE_ATTRIBUTE = "assignedRole";

    // Must stay aligned with the Login2Action username pattern and security.user_name
    // varchar(30): a name outside this charset or length could be created here but would be
    // rejected at login. The JSP maxlength mirrors the length but is browser-side only, so
    // this server-side check is the authoritative one.
    private static final String USER_NAME_PATTERN = "[a-zA-Z0-9]{1,30}";

    private final SecurityDao securityDao;
    private final SecurityManager securityManager;
    private final ProviderLoginCreationService loginCreationService;

    /** Constructor used by securityaddsecurity.jsp, which creates the helper with {@code new}. */
    public SecurityAddSecurityHelper() {
        this(SpringUtils.getBean(SecurityDao.class),
                SpringUtils.getBean(SecurityManager.class),
                SpringUtils.getBean(ProviderLoginCreationService.class));
    }

    SecurityAddSecurityHelper(SecurityDao securityDao,
                              SecurityManager securityManager,
                              ProviderLoginCreationService loginCreationService) {
        this.securityDao = securityDao;
        this.securityManager = securityManager;
        this.loginCreationService = loginCreationService;
    }

    /**
     * Adds a sec record (i.e. user login information) for the providers.
     * <p/>
     * Processing status is available as a "message" variable.
     *
     * @param pageContext JSP page context
     */
    public void addProvider(PageContext pageContext) {
        String message = process(pageContext);
        pageContext.setAttribute("message", message);
    }

    /**
     * Role names the logged-in administrator may give a new login, for the form's dropdown.
     *
     * @param pageContext JSP page context of the Add a Login Record form
     * @return assignable role names; never {@code null}
     */
    public List<String> assignableRoleNames(PageContext pageContext) {
        return loginCreationService.assignableRoleNames(
                LoggedInInfo.getLoggedInInfoFromSession(pageContext.getSession()));
    }

    private String validatePassword(String password, String confirmation) {
        if (password == null || password.isEmpty()) return "admin.securityaddsecurity.msgPasswordInvalid";
        if (!password.equals(confirmation)) return "admin.securityrecord.msgPasswordNotConfirmed";
        return PasswordPolicy.validate(password, CarlosProperties.getInstance()).isValid()
                ? null : "admin.securityaddsecurity.msgPasswordInvalid";
    }

    /** The unsaved login row for an already validated request; the password is encoded here. */
    private Security newLogin(ServletRequest request, String providerNo, String userName, String password) {
        Security s = new Security();
        s.setUserName(userName);
        s.setPassword(securityManager.encodePassword(password));
        s.setProviderNo(providerNo);
        s.setPin(request.getParameter("pin"));
        s.setBExpireset(request.getParameter("b_ExpireSet") == null ? 0 : Integer.parseInt(request.getParameter("b_ExpireSet")));
        s.setDateExpiredate(MyDateFormat.getSysDate(request.getParameter("date_ExpireDate")));
        s.setBLocallockset(request.getParameter("b_LocalLockSet") == null ? 0 : Integer.parseInt(request.getParameter("b_LocalLockSet")));
        s.setBRemotelockset(request.getParameter("b_RemoteLockSet") == null ? 0 : Integer.parseInt(request.getParameter("b_RemoteLockSet")));
        s.setForcePasswordReset("1".equals(request.getParameter("forcePasswordReset")));
        s.setPasswordUpdateDate(new Date());
        s.setPinUpdateDate(new Date());
        if ("1".equals(request.getParameter("enableMfa"))) {
            s.setUsingMfa(Boolean.TRUE);
            s.setBLocallockset(0);
            s.setBRemotelockset(0);
        } else {
            s.setUsingMfa(Boolean.FALSE);
        }
        return s;
    }

    private String process(PageContext pageContext) {
        ServletRequest request = pageContext.getRequest();


        String userName = request.getParameter("user_name") == null ? "" : request.getParameter("user_name").trim();
        if (!userName.matches(USER_NAME_PATTERN)) {
            return "admin.securityaddsecurity.msgUserNameInvalid";
        }

        String password = request.getParameter("password");
        String passwordError = validatePassword(password, request.getParameter("conPassword"));
        if (passwordError != null) return passwordError;

        String providerNo = request.getParameter("provider_no");
        boolean isUserRecordAlreadyCreatedForProvider = !securityDao.findByProviderNo(providerNo).isEmpty();
        if (isUserRecordAlreadyCreatedForProvider) return "admin.securityaddsecurity.msgLoginAlreadyExistsForProvider";

        boolean isUserAlreadyExists = !securityDao.findByUserName(userName).isEmpty();
        if (isUserAlreadyExists) return "admin.securityaddsecurity.msgAdditionFailureDuplicate";

        // A direct POST can name any provider: accept only one the form's dropdown would have
        // offered this administrator, since the login now carries a role as well.
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(pageContext.getSession());
        if (!loginCreationService.isEligibleProvider(loggedInInfo, providerNo)) {
            return "admin.securityaddsecurity.msgAdditionFailure";
        }

        // The browser form requires a choice too, but a direct POST can omit or forge it: only a
        // role from the same list the dropdown offered this administrator is accepted.
        String roleName = request.getParameter("role_name");
        if (roleName == null || !loginCreationService.assignableRoleNames(loggedInInfo).contains(roleName)) {
            return "admin.securityaddsecurity.msgRoleInvalid";
        }

        Security s = newLogin(request, providerNo, userName, password);

        try {
            // Writes the login, the role and both audit entries in one transaction.
            loginCreationService.createLoginWithRole(loggedInInfo, s, roleName);
        } catch (ProviderLoginCreationService.UnknownRoleException _) {
            // The role was deleted between the list check and the write; nothing was saved.
            return "admin.securityaddsecurity.msgRoleInvalid";
        }

        pageContext.setAttribute(ASSIGNED_ROLE_ATTRIBUTE, roleName);

        return "admin.securityaddsecurity.msgAdditionSuccess";
    }
}
