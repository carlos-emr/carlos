//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.web;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.caisi_integrator.ws.CachedFacility;
import ca.openosp.openo.commn.dao.IntegratorConsentDao;
import ca.openosp.openo.commn.model.IntegratorConsent;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.IntegratorConsent.ConsentStatus;
import ca.openosp.openo.commn.model.IntegratorConsent.SignatureStatus;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public class ManageConsent {
    private static Logger logger = MiscUtils.getLogger();
    private static IntegratorConsentDao integratorConsentDao = (IntegratorConsentDao) SpringUtils.getBean(IntegratorConsentDao.class);
    private static ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

    private LoggedInInfo loggedInInfo;
    private int clientId = -1;
    /**
     * consentToView is null if editing consent, if viewing consent it should have the consent to view.
     */
    private IntegratorConsent previousConsentToView = null;

    private boolean integratorServerError = false;

    public ManageConsent(LoggedInInfo loggedInInfo, int clientId) {
        this.loggedInInfo = loggedInInfo;
        this.clientId = clientId;
    }

    /**
     * It's safe to pass in null, it's ignore it. The string should be a number though and will be casted to one. It just seemed better to cast it here than in the jsp to reduce code in the jsp.
     */
    public void setViewConsentId(String consentId) {
        if (consentId != null) {
            previousConsentToView = integratorConsentDao.find(Integer.parseInt(consentId));
        }
    }

    public Collection<CachedFacility> getAllFacilitiesToDisplay(LoggedInInfo loggedInInfo) {
        try {
            if (previousConsentToView == null) return (getAllRemoteFacilities());
            else return (getPreviousConsentFacilities(loggedInInfo));
        } catch (Exception e) {
            integratorServerError = true;
            logger.error("unexpected error", e);
            return (null);
        }
    }

    private Collection<CachedFacility> getPreviousConsentFacilities(LoggedInInfo loggedInInfo) throws MalformedURLException {
        ArrayList<CachedFacility> results = new ArrayList<CachedFacility>();

        for (Integer remoteFacilityId : previousConsentToView.getConsentToShareData().keySet()) {
            CachedFacility cachedFacility = CaisiIntegratorManager.getRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteFacilityId);
            results.add(cachedFacility);
        }

        return (results);
    }

    private Collection<CachedFacility> getAllRemoteFacilities() throws MalformedURLException {
        List<CachedFacility> results = CaisiIntegratorManager.getRemoteFacilities(loggedInInfo, loggedInInfo.getCurrentFacility());
        return (results);
    }

    public boolean displayAsCheckedExcludeFacility(int remoteFacilityId) {
        if (previousConsentToView == null) {
            IntegratorConsent result = integratorConsentDao.findLatestByFacilityDemographic(loggedInInfo.getCurrentFacility().getId(), clientId);
            if (result != null) {
                Boolean checked = result.getConsentToShareData().get(remoteFacilityId);
                if (checked == null) return (false);
                else return (!checked);
            }

            return (false);
        } else {
            return (!previousConsentToView.getConsentToShareData().get(remoteFacilityId));
        }
    }

    public boolean displayAsCheckedExcludeMentalHealthData() {
        if (previousConsentToView == null) {
            IntegratorConsent result = integratorConsentDao.findLatestByFacilityDemographic(loggedInInfo.getCurrentFacility().getId(), clientId);
            if (result != null) {
                return (result.isExcludeMentalHealthData());
            }

            return (false);
        } else {
            return (previousConsentToView.isExcludeMentalHealthData());
        }
    }

    public boolean useDigitalSignatures() {
        return (loggedInInfo.getCurrentFacility().isEnableDigitalSignatures());
    }

    public Integer getPreviousConsentDigitalSignatureId() {
        return (previousConsentToView.getDigitalSignatureId());
    }

    public boolean displayAsSelectedConsentStatus(ConsentStatus status) {
        if (previousConsentToView == null) return (status == ConsentStatus.REVOKED);
        else return (previousConsentToView.getClientConsentStatus() == status);
    }

    public boolean displayAsSelectedSignatureStatus(SignatureStatus status) {
        if (previousConsentToView == null) return false;
        else return (previousConsentToView.getSignatureStatus() == status);
    }

    public boolean displayAsSelectedExpiry(int months) {
        if (previousConsentToView == null) return (months == -1);
        else {
            if (previousConsentToView.getExpiry() == null) return (months == -1);
            else {
                GregorianCalendar cal1 = new GregorianCalendar();
                cal1.setTime(previousConsentToView.getCreatedDate());
                cal1.add(Calendar.MONTH, months);

                return (DateUtils.isSameDay(cal1.getTime(), previousConsentToView.getExpiry()));
            }
        }
    }

    public boolean disableEdit() {
        if (previousConsentToView != null) return (true);

        return (integratorServerError);
    }

    public String getPreviousConsentProvider() {
        Provider provider = providerDao.getProvider(previousConsentToView.getProviderNo());
        return (provider.getFormattedName());
    }

    public String getPreviousConsentDate() {
        return (DateFormatUtils.ISO_DATETIME_FORMAT.format(previousConsentToView.getCreatedDate()).replace('T', ' '));
    }
}
