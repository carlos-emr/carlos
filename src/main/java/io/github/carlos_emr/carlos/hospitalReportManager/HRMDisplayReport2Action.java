/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.hospitalReportManager;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMReportCriteria;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ModelDriven;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for displaying an HRM report to a provider.
 *
 * <p>Uses the {@link ModelDriven} pattern with {@link HRMReportCriteria} to receive
 * search/display criteria from the request. Requires {@code _hrm} read privilege.</p>
 *
 * @see HRMReportCriteria
 * @see HRMDocumentToProvider
 * @since 2008-11-05
 */
public class HRMDisplayReport2Action extends ActionSupport implements ModelDriven<HRMReportCriteria> {

    // Model object holds all request parameters
    private HRMReportCriteria criteria = new HRMReportCriteria();

    /**
     * Returns the model object that holds request parameters for the HRM report display.
     *
     * @return HRMReportCriteria the report criteria model
     */
    @Override
    public HRMReportCriteria getModel() {
        return criteria;
    }

    // --- Existing service/dao wiring ---
    private static HRMDocumentToProviderDao hrmDocumentToProviderDao =
            (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Validates HRM read privileges and prepares the criteria model for the display JSP.
     *
     * @return String "display" result on success
     * @throws SecurityException if the provider lacks {@code _hrm} read privilege
     */
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