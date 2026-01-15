//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.hospitalReportManager;

import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToProviderDao;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToProvider;
import ca.openosp.openo.hospitalReportManager.model.HRMReportCriteria;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import com.opensymphony.xwork2.ModelDriven;
import org.apache.struts2.ServletActionContext;

public class HRMDisplayReport2Action extends ActionSupport implements ModelDriven<HRMReportCriteria> {

    // Model object holds all request parameters
    private HRMReportCriteria criteria = new HRMReportCriteria();

    @Override
    public HRMReportCriteria getModel() {
        return criteria;
    }

    // --- Existing service/dao wiring ---
    private static HRMDocumentToProviderDao hrmDocumentToProviderDao =
            (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();

        // check privilege
        if (!securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request),
                "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        // ModelDriven automatically exposes criteria object to JSP via value stack
        // For legacy JSP compatibility, also expose as request attributes
        request.setAttribute("criteria", criteria);

        return "display";
    }

    public static HRMDocumentToProvider getHRMDocumentFromProvider(String providerNo, Integer hrmDocumentId) {
        return hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(hrmDocumentId, providerNo);
    }
}