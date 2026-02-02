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

package io.github.carlos_emr.carlos.webserv;

import java.lang.management.ManagementFactory;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public final class WsUtils {
    private static ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * This method will check to see if the person is allowed to login, i.e. it will check username/expiry/password.
     * If the person is allowed it will setup the loggedInInfo data.
     *
     * @param security      can be null, it will return false for null.
     * @param securityToken can be the SecurityId's password, or a valid securityToken.
     */
    public static boolean checkAuthenticationAndSetLoggedInInfo(HttpServletRequest request, Security security, String securityToken) {
        if (security != null) {
            if (security.getDateExpiredate() != null && security.getDateExpiredate().before(new Date())) {
                logger.debug("sec record expired :" + security.getId());
                return (false);
            }

			if (checkToken(security, securityToken) || validatePassword(securityToken, security)) {
                LoggedInInfo loggedInInfo = new LoggedInInfo();
                loggedInInfo.setLoggedInSecurity(security);
                if (security.getProviderNo() != null) {
                    loggedInInfo.setLoggedInProvider(providerDao.getProvider(security.getProviderNo()));
                }

                LoggedInInfo.setLoggedInInfoIntoRequest(request, loggedInInfo);
                return (true);
            }
            logger.debug("token must not have been valid");
        }
        logger.debug("sec was null");
        return (false);
    }

	/**
	 * Validates the provided password against the password stored in the given Security object.
	 * If the password is not valid, it introduces a short delay to throttle potential brute-force attacks.
	 *
	 * @param password the password to validate.
	 * @param security the Security object containing the stored password.
	 * @return true if the password is valid, false otherwise.
	 */
	private static boolean validatePassword(String password, Security security) {
		SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);
		boolean isValid = securityManager.validatePassword(password, security);

		if(!isValid) {
			// sleep to throttle anyone trying to brute force hack passwords
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

		return isValid;
	}

    private static boolean checkToken(Security security, String securityToken) {
        return (securityToken.equals(generateSecurityToken(security)));
    }

    public static String generateSecurityToken(Security security) {
        // we'll add a randomish seed to a deterministic value unique to the user.
        // the randomish seed can be the jvm start time
        // the deterministic value unique to the user is a mostly unique rendition of their
        // password.

        long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        long mangling = getStringAsLongRendition(security.getPassword());
        return (String.valueOf(jvmStartTime + mangling));
    }

    /**
     * The purpose of this method is to generate a long from the given string.
     * It doesn't have to be anything in particular other than being deterministic
     * and relatively unique (no uniqueness guarantees required).
     */
    private static long getStringAsLongRendition(String s) {
        long result = 0;
        int modMax = 6;
        int mod = 0;

        for (char c : s.toCharArray()) {
            result = result + c * (long) Math.pow(100, mod);

            mod = (mod + 1) % modMax;
        }

        return (result);
    }
}
