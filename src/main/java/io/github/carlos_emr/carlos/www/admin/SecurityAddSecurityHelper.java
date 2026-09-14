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

import jakarta.servlet.ServletRequest;
import jakarta.servlet.jsp.PageContext;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;


/**
 * Helper class for securityaddsecurity.jsp page.
 */
public class SecurityAddSecurityHelper {

    private static final Logger logger = MiscUtils.getLogger();

    // Must stay aligned with the Login2Action username pattern and security.user_name
    // varchar(30): a name outside this charset or length could be created here but would be
    // rejected at login. The JSP maxlength mirrors the length but is browser-side only, so
    // this server-side check is the authoritative one.
    private static final String USER_NAME_PATTERN = "[a-zA-Z0-9]{1,30}";

    private SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
	private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);

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

    private String process(PageContext pageContext) {
        ServletRequest request = pageContext.getRequest();

		String digestedPassword = this.securityManager.encodePassword(request.getParameter("password"));
		String digestedPin = this.encodeOptionalPin(request.getParameter("pin"));

        String userName = request.getParameter("user_name") == null ? "" : request.getParameter("user_name").trim();
        if (!userName.matches(USER_NAME_PATTERN)) {
            return "admin.securityaddsecurity.msgUserNameInvalid";
        }

        boolean isUserRecordAlreadyCreatedForProvider = !securityDao.findByProviderNo(request.getParameter("provider_no")).isEmpty();
        if (isUserRecordAlreadyCreatedForProvider) return "admin.securityaddsecurity.msgLoginAlreadyExistsForProvider";

        boolean isUserAlreadyExists = !securityDao.findByUserName(userName).isEmpty();
        if (isUserAlreadyExists) return "admin.securityaddsecurity.msgAdditionFailureDuplicate";

        Security s = new Security();
        s.setUserName(userName);
        s.setPassword(digestedPassword);
        s.setProviderNo(request.getParameter("provider_no"));
        s.setPin(digestedPin);
        s.setBExpireset(parseLockSetting(request.getParameter("b_ExpireSet")));
        s.setDateExpiredate(MyDateFormat.getSysDate(request.getParameter("date_ExpireDate")));
        s.setBLocallockset(parseLockSetting(request.getParameter("b_LocalLockSet")));
        s.setBRemotelockset(parseLockSetting(request.getParameter("b_RemoteLockSet")));

        if (request.getParameter("forcePasswordReset") != null && request.getParameter("forcePasswordReset").equals("1")) {
            s.setForcePasswordReset(Boolean.TRUE);
        } else {
            s.setForcePasswordReset(Boolean.FALSE);
        }

        s.setPasswordUpdateDate(new Date());
        // Only stamp a PIN update when a PIN was actually stored, so a PIN-less account does not
        // look like it has a freshly rotated PIN.
        s.setPinUpdateDate(digestedPin == null ? null : new Date());

		if (request.getParameter("enableMfa") != null && request.getParameter("enableMfa").equals("1")) {
			s.setUsingMfa(Boolean.TRUE);
			s.setBLocallockset(0);
			s.setBRemotelockset(0);
		} else {
			s.setUsingMfa(Boolean.FALSE);
		}

        securityDao.persist(s);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(pageContext.getSession());
        LogAction.addLog(loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null, LogConst.ADD, LogConst.CON_SECURITY, userName, request.getRemoteAddr());

        return "admin.securityaddsecurity.msgAdditionSuccess";
    }

    /**
     * Hashes a submitted PIN, tolerating its absence.
     *
     * <p>The add form omits the PIN controls entirely when legacy PINs are globally disabled, and
     * disables them when MFA is selected; a disabled input is not submitted. In both cases the
     * request parameter is null. Hashing unconditionally would throw out of the password encoder
     * and fail provider creation before the record is ever persisted, so a missing or blank PIN is
     * preserved as no PIN at all.</p>
     *
     * @param rawPin The submitted PIN value, possibly null.
     * @return The hashed PIN, or null when no PIN was supplied.
     */
    private String encodeOptionalPin(String rawPin) {
        if (rawPin == null || rawPin.trim().isEmpty()) {
            return null;
        }
        return this.securityManager.encodePin(rawPin);
    }

    /**
     * Parses a lock-setting request value.
     *
     * <p>Valid form values are expected to be numeric flags, where 0 disables the lock and 1 enables
     * it. Missing or malformed values default to 0 so direct POSTs cannot fail provider creation with
     * an unhandled parsing exception.</p>
     *
     * @param value The submitted lock-setting value.
     * @return The parsed numeric flag, or 0 when the value is missing or invalid.
     */
    static int parseLockSetting(String value) {
        try {
            int parsed = value == null ? 0 : Integer.parseInt(value);
            return parsed == 1 ? 1 : 0;
        } catch (NumberFormatException e) {
            logger.warn("Invalid security lock setting submitted; defaulting to disabled");
            return 0;
        }
    }
}
