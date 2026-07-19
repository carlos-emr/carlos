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

package io.github.carlos_emr.carlos.webserv;

import java.util.List;

import javax.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.NotAuthorisedException;
import io.github.carlos_emr.carlos.webserv.transfer_objects.LoginResultTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.LoginResultTransfer2;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class LoginWs extends AbstractWs {
    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private SecurityDao securityDao = null;

    @Autowired
    private ProviderDao providerDao = null;

    /**
     * @throws NotAuthorisedException if password is incorrect
     * @deprecated 2015-01-28
     * <p>
     * Returns LoginResultTransfer on valid login, will be provided with a sec token too.
     */
    @Deprecated
    public LoginResultTransfer login(String userName, String password) throws NotAuthorisedException {
        logger.info("Login attempt : user=" + userName);
        logger.debug("Login attempt : p =" + password);

        List<Security> securities = securityDao.findByUserName(userName);
        Security security = null;

        if (securities.size() > 0) security = securities.get(0);

        if (WsUtils.checkAuthenticationAndSetLoggedInInfo(getHttpServletRequest(), security, password)) {
            LoginResultTransfer result = new LoginResultTransfer();
            result.setSecurityId(security.getSecurityNo());

            String securityToken = WsUtils.generateSecurityToken(security);
            result.setSecurityTokenKey(securityToken);

            return (result);
        }

        throw (new NotAuthorisedException("Invalid Username/Password"));
    }

    /**
     * @param password can be the users password or a valid token
     * @return LoginResultTransfer2 on valid login, will be provided with a sec token too.
     * @throws NotAuthorisedException if password is incorrect
     */
    public LoginResultTransfer2 login2(String userName, String password) throws NotAuthorisedException {
        logger.info("Login attempt : user=" + userName);
        logger.debug("Login attempt : p =" + password);

        List<Security> securities = securityDao.findByUserName(userName);
        Security security = null;

        if (securities.size() > 0) security = securities.get(0);

        if (WsUtils.checkAuthenticationAndSetLoggedInInfo(getHttpServletRequest(), security, password)) {
            LoginResultTransfer2 result = new LoginResultTransfer2();
            result.setSecurityId(security.getSecurityNo());

            String securityToken = WsUtils.generateSecurityToken(security);
            result.setSecurityTokenKey(securityToken);

            if (security.getProviderNo() != null) {
                Provider provider = providerDao.getProvider(security.getProviderNo());
                result.setProvider(ProviderTransfer.toTransfer(provider));
            }

            return (result);
        }

        throw (new NotAuthorisedException("Invalid Username/Password"));
    }
}
