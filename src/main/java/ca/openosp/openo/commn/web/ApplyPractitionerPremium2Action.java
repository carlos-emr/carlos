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

package ca.openosp.openo.commn.web;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.BillingONPremiumDao;
import ca.openosp.openo.commn.model.BillingONPremium;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

/**
 * @author mweston4
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ApplyPractitionerPremium2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private BillingONPremiumDao bPremiumDao = (BillingONPremiumDao) SpringUtils.getBean(BillingONPremiumDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        return applyPremium();
    }

    public String applyPremium() {
        String raHeaderNoStr = request.getParameter("rano");
        Integer raHeaderNo = Integer.parseInt(raHeaderNoStr);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        List<BillingONPremium> bPremiumList = bPremiumDao.getRAPremiumsByRaHeaderNo(raHeaderNo);

        for (BillingONPremium bPremium : bPremiumList) {

            String premiumId = String.valueOf(bPremium.getId());
            //check this providers is in OSCAR
            String providerNo = request.getParameter("providerNo" + premiumId);
            if (providerNo != null && !providerNo.isEmpty()) {
                ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
                Provider p = providerDao.getProvider(providerNo);

                String premiumSelected = request.getParameter("choosePremium" + premiumId);

                if (premiumSelected != null && premiumSelected.equals("Y")) {
                    bPremium.setStatus(true);
                } else {
                    bPremium.setStatus(false);
                    providerNo = null;
                }

                if (p != null) {
                    bPremium.setProviderNo(providerNo);
                    bPremiumDao.merge(bPremium);
                } else {
                    return "failure";
                }
            }
        }

        return SUCCESS;
    }
}
