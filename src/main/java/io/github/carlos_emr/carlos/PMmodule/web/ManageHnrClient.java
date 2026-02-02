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

import java.util.List;

import io.github.carlos_emr.carlos.ws.Client;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.caisi_integrator.ws.ConnectException_Exception;
import io.github.carlos_emr.carlos.caisi_integrator.ws.ConsentState;
import io.github.carlos_emr.carlos.caisi_integrator.ws.GetConsentTransfer;
import io.github.carlos_emr.carlos.casemgmt.dao.ClientImageDAO;
import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.commn.dao.ClientLinkDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.HnrDataValidationDao;
import io.github.carlos_emr.carlos.commn.model.ClientLink;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.HnrDataValidation;
import io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class ManageHnrClient {
    private static Logger logger = MiscUtils.getLogger();
    private static DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private static ClientLinkDao clientLinkDao = (ClientLinkDao) SpringUtils.getBean(ClientLinkDao.class);
    private static ClientImageDAO clientImageDAO = (ClientImageDAO) SpringUtils.getBean(ClientImageDAO.class);
    private static HnrDataValidationDao hnrDataValidationDao = (HnrDataValidationDao) SpringUtils.getBean(HnrDataValidationDao.class);

    private Demographic demographic = null;
    private ClientImage clientImage = null;
    private ClientLink clientLink = null;
    private Client hnrClient = null;
    private boolean pictureValidated = false;
    private boolean hcInfoValidated = false;
    private boolean otherValidated = false;
    private LoggedInInfo loggedInInfo;

    public ManageHnrClient(LoggedInInfo loggedInInfo, Integer demographicId) {
        this.loggedInInfo = loggedInInfo;
        demographic = demographicDao.getDemographicById(demographicId);
        clientImage = clientImageDAO.getClientImage(demographicId);

        // we're only dealing with 1 hnr entry even if there's multiple because there should
        // only be 1, a minor issue about some of this code not being atomic makes multiple
        // entries theoretically possible though in reality it should never happen.
        List<ClientLink> temp = clientLinkDao.findByFacilityIdClientIdType(loggedInInfo.getCurrentFacility().getId(), demographicId, true, ClientLink.Type.HNR);
        if (temp.size() > 0) clientLink = temp.get(0);

        if (loggedInInfo.getCurrentFacility().isEnableHealthNumberRegistry() && loggedInInfo.getCurrentFacility().isIntegratorEnabled() && clientLink != null) {
            try {
                hnrClient = CaisiIntegratorManager.getHnrClient(loggedInInfo, loggedInInfo.getCurrentFacility(), clientLink.getRemoteLinkId());
            } catch (ConnectException_Exception e) {
                logger.error("Error Connecting to HNR server", e);
            } catch (Exception e) {
                logger.error("Unexpected error", e);
            }
        }

        HnrDataValidation tempValidation = hnrDataValidationDao.findMostCurrentByFacilityIdClientIdType(loggedInInfo.getCurrentFacility().getId(), demographicId, HnrDataValidation.Type.PICTURE);
        pictureValidated = (tempValidation != null && tempValidation.isValidAndMatchingCrc(clientImage.getImage_data()));

        tempValidation = hnrDataValidationDao.findMostCurrentByFacilityIdClientIdType(loggedInInfo.getCurrentFacility().getId(), demographicId, HnrDataValidation.Type.HC_INFO);
        hcInfoValidated = (tempValidation != null && tempValidation.isValidAndMatchingCrc(HnrDataValidation.getHcInfoValidationBytes(demographic)));

        tempValidation = hnrDataValidationDao.findMostCurrentByFacilityIdClientIdType(loggedInInfo.getCurrentFacility().getId(), demographicId, HnrDataValidation.Type.OTHER);
        otherValidated = (tempValidation != null && tempValidation.isValidAndMatchingCrc(HnrDataValidation.getOtherInfoValidationBytes(demographic)));
    }

    public Demographic getDemographic() {
        return demographic;
    }

    public Client getHnrClient() {
        return hnrClient;
    }

    public boolean isImageValidateable() {
        return (HnrDataValidation.isImageValidated(clientImage));
    }

    public boolean isHcInfoValidatable() {
        return (HnrDataValidation.isHcInfoValidateable(demographic));
    }

    public boolean isOtherInfoValidatable() {
        return (HnrDataValidation.isOtherInfoValidateable(demographic));
    }

    public String getLocalClientImageUrl() {
        if (clientImage == null) return (ClientImage.imageMissingPlaceholderUrl);
        else
            return ("/imageRenderingServlet?source=" + ImageRenderingServlet.Source.local_client.name() + "&clientId=" + demographic.getDemographicNo());
    }

    public String getHnrClientImageUrl() {
        if (hnrClient == null || hnrClient.getImage() == null) return (ClientImage.imageMissingPlaceholderUrl);
        else
            return ("/imageRenderingServlet?source=" + ImageRenderingServlet.Source.hnr_client.name() + "&linkingId=" + clientLink.getRemoteLinkId());
    }

    public String getRemoteFormatedBirthDate() {
        String date = "";

        if (hnrClient != null && hnrClient.getBirthDate() != null) {
            date = DateFormatUtils.ISO_DATE_FORMAT.format(hnrClient.getBirthDate());
        }

        return (date);
    }

    public String getRemoteFormatedHinStartDate() {
        String date = "";

        if (hnrClient != null && hnrClient.getHinValidStart() != null) {
            date = DateFormatUtils.ISO_DATE_FORMAT.format(hnrClient.getHinValidStart());
        }

        return (date);
    }

    public String getRemoteFormatedHinEndDate() {
        String date = "";

        if (hnrClient != null && hnrClient.getHinValidEnd() != null) {
            date = DateFormatUtils.ISO_DATE_FORMAT.format(hnrClient.getHinValidEnd());
        }

        return (date);
    }

    public String getLocalFormatedHinStartDate() {
        String date = "";

        if (demographic != null && demographic.getEffDate() != null) {
            date = DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getEffDate());
        }

        return (date);
    }

    public String getLocalFormatedHinEndDate() {
        String date = "";

        if (demographic != null && demographic.getHcRenewDate() != null) {
            date = DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getHcRenewDate());
        }

        return (date);
    }

    public String getRemoteGender() {
        String gender = "";

        if (hnrClient != null && hnrClient.getGender() != null) {
            gender = hnrClient.getGender().name();
        }

        return (gender);
    }

    /**
     * The actionString is the inverse of the current state.
     * i.e. if the picture is currently validated, then the allowable action is to "invalidate" it .
     */
    public String getPictureValidationActionString() {
        if (pictureValidated) return ("invalidate");
        else return ("validate");
    }

    /**
     * The actionString is the inverse of the current state.
     * i.e. if the picture is currently validated, then the allowable action is to "invalidate" it .
     */
    public String getHcInfoValidationActionString() {
        if (hcInfoValidated) return ("invalidate");
        else return ("validate");
    }

    /**
     * The actionString is the inverse of the current state.
     * i.e. if the picture is currently validated, then the allowable action is to "invalidate" it .
     */
    public String getOtherInfoValidationActionString() {
        if (otherValidated) return ("invalidate");
        else return ("validate");
    }

    private boolean hasConsented() {
        try {
            GetConsentTransfer consent = CaisiIntegratorManager.getConsentState(loggedInInfo, loggedInInfo.getCurrentFacility(), demographic.getDemographicNo());
            return (consent != null && consent.getConsentState() == ConsentState.ALL);
        } catch (Exception e) {
            logger.debug("Exception getting consent state.", e);
            return (false);
        }
    }

    public boolean canSendToHnr() {
        return (pictureValidated && hasConsented());
    }
}
