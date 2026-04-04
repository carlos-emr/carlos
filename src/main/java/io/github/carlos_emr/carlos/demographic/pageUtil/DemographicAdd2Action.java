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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.CountryCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.CountryCode;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.demographic.data.ProvinceNames;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Struts2 action that loads setup data for the demographic add (new patient) page
 * and sets it as request attributes for the JSP fragments under
 * {@code /WEB-INF/jsp/demographic/}.
 *
 * @since 2026-04-04
 */
public class DemographicAdd2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        HttpSession session = request.getSession();
        if (session.getAttribute("user") == null) {
            return "logout";
        }

        String curUser_no = (String) session.getAttribute("user");
        CarlosProperties oscarProps = CarlosProperties.getInstance();
        String prov = StringUtils.trimToEmpty(oscarProps.getProperty("billregion", "")).toUpperCase();

        // --- Current date ---
        GregorianCalendar now = new GregorianCalendar();
        String curYear = Integer.toString(now.get(Calendar.YEAR));
        String curMonth = Integer.toString(now.get(Calendar.MONTH) + 1);
        if (curMonth.length() < 2) curMonth = "0" + curMonth;
        String curDay = Integer.toString(now.get(Calendar.DAY_OF_MONTH));
        if (curDay.length() < 2) curDay = "0" + curDay;

        String billingCentre = StringUtils.trimToEmpty(oscarProps.getProperty("billcenter", "")).toUpperCase();
        String defaultCity = "ON".equals(prov) && "N".equals(billingCentre) ? "Toronto" : "";

        // --- Country codes ---
        CountryCodeDao ccDAO = SpringUtils.getBean(CountryCodeDao.class);
        List<CountryCode> countryList = ccDAO.getAllCountryCodes();

        // --- HC Type ---
        UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        String HCType = "";
        UserProperty HCTypeProp = userPropertyDAO.getProp(curUser_no, UserProperty.HC_TYPE);
        if (HCTypeProp != null) {
            HCType = HCTypeProp.getValue();
        } else {
            HCType = oscarProps.getProperty("hctype", "");
            if (HCType == null || HCType.isEmpty()) {
                HCType = oscarProps.getProperty("billregion", "");
            }
        }
        String defaultProvince = HCType;

        // --- Province names ---
        ProvinceNames pNames = ProvinceNames.getInstance();

        // --- Provider lists ---
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> doctors = providerDao.getActiveProvidersByRole("doctor");
        List<Provider> nurses = providerDao.getActiveProvidersByRole("nurse");
        List<Provider> midwifes = providerDao.getActiveProvidersByRole("midwife");

        // --- Privacy/consent ---
        String privateConsentEnabledProp = oscarProps.getProperty("privateConsentEnabled");
        boolean privateConsentEnabled = "true".equals(privateConsentEnabledProp);

        // --- Patient consent module ---
        if (oscarProps.getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
            PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
            request.setAttribute("consentTypes", patientConsentManager.getConsentTypes());
        }

        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        // --- Set request attributes ---
        request.setAttribute("curUser_no", curUser_no);
        request.setAttribute("oscarProps", oscarProps);
        request.setAttribute("prov", prov);
        request.setAttribute("curYear", curYear);
        request.setAttribute("curMonth", curMonth);
        request.setAttribute("curDay", curDay);
        request.setAttribute("billingCentre", billingCentre);
        request.setAttribute("defaultCity", defaultCity);
        request.setAttribute("countryList", countryList);
        request.setAttribute("ccDAO", ccDAO);
        request.setAttribute("userPropertyDAO", userPropertyDAO);
        request.setAttribute("HCType", HCType);
        request.setAttribute("defaultProvince", defaultProvince);
        request.setAttribute("pNames", pNames);
        request.setAttribute("privateConsentEnabled", privateConsentEnabled);
        request.setAttribute("today", today);
        request.setAttribute("doctors", doctors);
        request.setAttribute("nurses", nurses);
        request.setAttribute("midwifes", midwifes);

        // DAOs/managers needed by JSP fragments
        request.setAttribute("providerDao", providerDao);
        request.setAttribute("demographicDao", SpringUtils.getBean(DemographicDao.class));
        request.setAttribute("waitingListNameDao", SpringUtils.getBean(WaitingListNameDao.class));
        request.setAttribute("eformDao", SpringUtils.getBean(EFormDao.class));
        request.setAttribute("programDao", SpringUtils.getBean(ProgramDao.class));
        request.setAttribute("programManager", SpringUtils.getBean(ProgramManager.class));
        request.setAttribute("programManager2", SpringUtils.getBean(ProgramManager2.class));
        request.setAttribute("professionalSpecialistDao", SpringUtils.getBean(ProfessionalSpecialistDao.class));

        // Pass through appointment context parameters
        request.setAttribute("fromAppt", request.getParameter("fromAppt"));
        request.setAttribute("originalPage", request.getParameter("originalPage"));

        return SUCCESS;
    }
}
