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


package ca.openosp.openo.login;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.service.AcceptableUseAgreementManager;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;

/**
 * @author rjonasz
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class LoginAgreement2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger _logger = MiscUtils.getLogger();

    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    public String execute() throws ServletException {

        String userAgreement = request.getParameter("submit");
        String user = (String) request.getSession().getAttribute("user");
        if (userAgreement.equalsIgnoreCase("refuse")) {
            _logger.debug(user + " refused agreement");
            LogAction.addLog(user, LogConst.REFUSED, LogConst.CON_LOGIN_AGREEMENT, userAgreement, request.getRemoteAddr(), null, AcceptableUseAgreementManager.getAUAText());
            return "Logout";

        } else if (userAgreement.equalsIgnoreCase("accept")) {
            _logger.debug(user + " accepted agreement");
            Provider provider = providerDao.getProvider(user);
            Date now = new Date();
            provider.setSignedConfidentiality(now);
            providerDao.updateProvider(provider);
        }

        LogAction.addLog(user, LogConst.ACK, LogConst.CON_LOGIN_AGREEMENT, userAgreement, request.getRemoteAddr(), null, AcceptableUseAgreementManager.getAUAText());

        String proceedURL = (String) request.getSession().getAttribute("proceedURL");
        request.getSession().setAttribute("proceedURL", null);

        try {
            response.sendRedirect(proceedURL);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

}
