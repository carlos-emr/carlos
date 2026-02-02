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

package io.github.carlos_emr.carlos.PMmodule.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.caisi_integrator.ws.CachedFacility;
import io.github.carlos_emr.carlos.commn.dao.IntegratorConsentDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.IntegratorConsent;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class ManageConsentAction {
    private static Logger logger = MiscUtils.getLogger();
    private static IntegratorConsentDao integratorConsentDao = (IntegratorConsentDao) SpringUtils.getBean(IntegratorConsentDao.class);

    private IntegratorConsent consent = new IntegratorConsent();
    private String signatureRequestId = null;
    private Integer clientId = null;
    private LoggedInInfo loggedInInfo;

    public ManageConsentAction(LoggedInInfo loggedInInfo, Integer clientId) throws MalformedURLException {
        this.loggedInInfo = loggedInInfo;
        this.clientId = clientId;

        consent.setDemographicId(clientId);
        consent.setFacilityId(loggedInInfo.getCurrentFacility().getId());
        consent.setProviderNo(loggedInInfo.getLoggedInProviderNo());

        for (CachedFacility cachedFacility : CaisiIntegratorManager.getRemoteFacilities(loggedInInfo, loggedInInfo.getCurrentFacility())) {
            consent.getConsentToShareData().put(cachedFacility.getIntegratorFacilityId(), true);
        }
    }

    /**
     * This method is expected to be used by the detail consent where each check box maps to each consent bit and every entry must be set manually.
     *
     * @param s is of the format "consent.<remoteFacilityId>.<consentField>", i.e. "consent.1.hic"
     */
    public void addExclude(String s) {
        String[] splitTemp = s.split("\\.");
        int remoteFacilityId = Integer.parseInt(splitTemp[1]);

        if ("excludeShareData".equals(splitTemp[2])) {
            consent.getConsentToShareData().put(remoteFacilityId, false);
        } else {
            logger.error("unexpected consent bit : " + s);
        }
    }

    /**
     * @throws IOException if expecting a signature but missing one
     */
    public void storeAllConsents() throws IOException {

		DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
		DigitalSignature digitalSignature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo, signatureRequestId, clientId, ModuleType.CONSULTATION);
        if (digitalSignature != null) consent.setDigitalSignatureId(digitalSignature.getId());

        integratorConsentDao.persist(consent);

        CaisiIntegratorManager.pushConsent(loggedInInfo, loggedInInfo.getCurrentFacility(), consent);
    }

    public void setExcludeMentalHealthData(Boolean b) {
        consent.setExcludeMentalHealthData(b);
    }

    public void setConsentStatus(String s) {
        consent.setClientConsentStatus(IntegratorConsent.ConsentStatus.valueOf(s));
    }

    public void setSignatureStatus(String s) {
        consent.setSignatureStatus(IntegratorConsent.SignatureStatus.valueOf(s));
    }

    public void setExpiry(String s) {
        int months = -1;
        if (s != null)
            months = Integer.parseInt(s);

        if (months == -1)
            consent.setExpiry(null);
        else {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(consent.getCreatedDate());
            cal.add(Calendar.MONTH, months);
            consent.setExpiry(cal.getTime());
        }
    }

    public void setSignatureRequestId(String signatureRequestId) {
        this.signatureRequestId = signatureRequestId;
    }
}
