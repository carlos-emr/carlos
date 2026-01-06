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


package ca.openosp.openo.PMmodule.caisi_integrator;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.caisi_integrator.ws.CachedProvider;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk;
import ca.openosp.openo.caisi_integrator.ws.ProviderCommunicationTransfer;
import ca.openosp.openo.caisi_integrator.ws.ProviderWs;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ca.openosp.OscarProperties;
import ca.openosp.openo.tickler.TicklerCreator;
import ca.openosp.openo.util.DateUtils;

/**
 * Utility class providing helper methods for integrator conformance testing and data synchronization.
 * <p>
 * This class contains static utility methods used primarily during conformance testing and
 * validation of the integrator system. It provides functionality for:
 * <ul>
 *   <li>Creating local ticklers from remote provider follow-up messages</li>
 *   <li>Copying demographic data from linked remote demographics to local records</li>
 *   <li>Comparing local and remote demographic data to detect discrepancies</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Note:</strong> Many methods in this class are controlled by the
 * {@code ENABLE_CONFORMANCE_ONLY_FEATURES} property and should only be active
 * during conformance testing, not in production environments.
 * </p>
 * 
 * @see CaisiIntegratorManager
 */
public final class ConformanceTestHelper {
    /** Logger instance for this class */
    private static Logger logger = MiscUtils.getLogger();
    
    /** 
     * Flag indicating whether conformance-only test features are enabled.
     * Controlled by the ENABLE_CONFORMANCE_ONLY_FEATURES property.
     */
    public static boolean enableConformanceOnlyTestFeatures = Boolean.parseBoolean(OscarProperties.getInstance().getProperty("ENABLE_CONFORMANCE_ONLY_FEATURES"));

    /**
     * Creates local ticklers from remote provider communication follow-up messages.
     * <p>
     * This method retrieves follow-up provider communications from the integrator service
     * and creates corresponding ticklers in the local system. It's used to ensure that
     * follow-up tasks assigned by remote providers are visible to local providers.
     * </p>
     * <p>
     * The method performs the following steps:
     * <ol>
     *   <li>Retrieves all active follow-up communications for the logged-in provider</li>
     *   <li>Extracts the destination demographic and note from each communication</li>
     *   <li>Creates a local tickler with the remote provider information</li>
     *   <li>Deactivates the remote communication after processing</li>
     * </ol>
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     */
    public static void populateLocalTicklerWithRemoteProviderMessageFollowUps(LoggedInInfo loggedInInfo) {
        try {
            // Get the provider web service for the current facility
            ProviderWs providerWs = CaisiIntegratorManager.getProviderWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            // Retrieve all follow-up communications for the logged-in provider
            List<ProviderCommunicationTransfer> followUps = providerWs.getProviderCommunications(loggedInInfo.getLoggedInProviderNo(), "FOLLOWUP", true);

            if (followUps == null) return;

            logger.debug("Folowups found : " + followUps.size());

            // Process each follow-up communication
            for (ProviderCommunicationTransfer providerCommunication : followUps) {
                // Parse the XML data to extract demographic and note information
                Document doc = XmlUtils.toDocument(providerCommunication.getData());
                Node root = doc.getFirstChild();
                String demographicId = XmlUtils.getChildNodeTextContents(root, "destinationDemographicId");
                String note = XmlUtils.getChildNodeTextContents(root, "note");

                TicklerCreator t = new TicklerCreator();

                logger.debug("Create tickler : " + demographicId + ", " + providerCommunication.getDestinationProviderId() + ", " + note);

                // Look up the sending provider's information to add context to the tickler
                FacilityIdStringCompositePk senderProviderId = new FacilityIdStringCompositePk();
                senderProviderId.setIntegratorFacilityId(providerCommunication.getSourceIntegratorFacilityId());
                senderProviderId.setCaisiItemId(providerCommunication.getSourceProviderId());
                CachedProvider senderProvider = CaisiIntegratorManager.getProvider(loggedInInfo, loggedInInfo.getCurrentFacility(), senderProviderId);
                if (senderProvider != null) {
                    // Prepend sender information to the note
                    note = "Sent by remote providers : " + senderProvider.getLastName() + ", " + senderProvider.getFirstName() + "<br />--------------------<br />" + note;
                }

                // Create the local tickler
                t.createTickler(loggedInInfo, demographicId, providerCommunication.getDestinationProviderId(), note);

                // Mark the remote communication as processed
                providerWs.deactivateProviderCommunication(providerCommunication.getId());
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    /**
     * Copies demographic data from linked remote demographics to the local demographic record.
     * <p>
     * This method retrieves all demographics directly linked to a local demographic in the
     * integrator system and copies non-null field values from the first linked record to
     * the local record. This is useful for synchronizing demographic information across
     * facilities.
     * </p>
     * <p>
     * The method updates fields such as:
     * <ul>
     *   <li>Name fields (first, last)</li>
     *   <li>Date of birth</li>
     *   <li>Health insurance information (HIN, type, version, dates)</li>
     *   <li>Contact information (address, city, province, phone)</li>
     *   <li>Gender</li>
     *   <li>SIN</li>
     * </ul>
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param localDemographicId the ID of the local demographic to update
     */
    public static void copyLinkedDemographicsPropertiesToLocal(LoggedInInfo loggedInInfo, Integer localDemographicId) {
        try {
            // Get the demographic web service for the current facility
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            // Retrieve directly linked demographics from the integrator
            List<DemographicTransfer> directLinks = demographicWs.getDirectlyLinkedDemographicsByDemographicId(localDemographicId);

            logger.debug("found linked demographics size:" + directLinks.size());

            if (directLinks.size() > 0) {
                // Use the first linked demographic as the source
                DemographicTransfer demographicTransfer = directLinks.get(0);

                logger.debug("remoteDemographic:" + ReflectionToStringBuilder.toString(demographicTransfer));

                // Load the local demographic
                DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
                Demographic demographic = demographicDao.getDemographicById(localDemographicId);

                // Copy all non-null fields from the remote demographic to the local record
                CaisiIntegratorManager.copyDemographicFieldsIfNotNull(demographicTransfer, demographic);

                // Update the roster date to reflect the synchronization
                demographic.setRosterDate(new Date());

                // Persist the changes
                demographicDao.save(demographic);
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    /**
     * Checks if any directly linked remote demographics have different data than the local record.
     * <p>
     * This method compares the local demographic record with the first linked remote demographic
     * to identify discrepancies. It checks all major demographic fields and returns true if any
     * differences are detected.
     * </p>
     * <p>
     * Fields compared include:
     * <ul>
     *   <li>Birth date</li>
     *   <li>Name fields</li>
     *   <li>Address and city</li>
     *   <li>HIN and related health insurance fields</li>
     *   <li>Gender</li>
     *   <li>Province</li>
     *   <li>SIN</li>
     *   <li>Phone numbers</li>
     * </ul>
     * </p>
     * 
     * @param loggedInInfo the current user's session information
     * @param localDemographicId the ID of the local demographic to compare
     * @return true if any differences are detected, false otherwise
     */
    public static boolean hasDifferentRemoteDemographics(LoggedInInfo loggedInInfo, Integer localDemographicId) {
        boolean ret = false;
        try {
            // Get the demographic web service
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            // Retrieve linked demographics
            List<DemographicTransfer> directLinks = demographicWs.getDirectlyLinkedDemographicsByDemographicId(localDemographicId);

            logger.debug("found linked demographics size:" + directLinks.size());

            if (directLinks.size() > 0) {
                // Compare with the first linked demographic
                DemographicTransfer demographicTransfer = directLinks.get(0);

                logger.debug("remoteDemographic:" + ReflectionToStringBuilder.toString(demographicTransfer));

                // Load the local demographic for comparison
                DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
                Demographic demographic = demographicDao.getDemographicById(localDemographicId);

                // Compare each field, marking as different if remote has a value that differs from local
                if (demographicTransfer.getBirthDate() != null && !(DateUtils.getNumberOfDaysBetweenTwoDates(demographicTransfer.getBirthDate(), demographic.getBirthDay()) == 0))
                    ret = true;
                if (demographicTransfer.getCity() != null && !demographicTransfer.getCity().equalsIgnoreCase(demographic.getCity()))
                    ret = true;
                if (demographicTransfer.getFirstName() != null && !demographicTransfer.getFirstName().equals(demographic.getFirstName()))
                    ret = true;
                if (demographicTransfer.getGender() != null && !demographicTransfer.getGender().toString().equals(demographic.getSex()))
                    ret = true;
                if (demographicTransfer.getHin() != null && !demographicTransfer.getHin().equals(demographic.getHin()))
                    ret = true;
                if (demographicTransfer.getHinType() != null && !demographicTransfer.getHinType().equals(demographic.getHcType()))
                    ret = true;
                if (demographicTransfer.getHinVersion() != null && !demographicTransfer.getHinVersion().equals(demographic.getVer()))
                    ret = true;
                if (isRemoteDateDifferent(DateUtils.toGregorianCalendar(demographic.getEffDate()), demographicTransfer.getHinValidStart()))
                    ret = true;
                if (isRemoteDateDifferent(DateUtils.toGregorianCalendar(demographic.getHcRenewDate()), demographicTransfer.getHinValidEnd()))
                    ret = true;
                if (demographicTransfer.getLastName() != null && !demographicTransfer.getLastName().equals(demographic.getLastName()))
                    ret = true;
                if (demographicTransfer.getProvince() != null && !demographicTransfer.getProvince().equalsIgnoreCase(demographic.getProvince()))
                    ret = true;
                if (demographicTransfer.getSin() != null && !demographicTransfer.getSin().equals(demographic.getSin()))
                    ret = true;
                if (demographicTransfer.getStreetAddress() != null && !demographicTransfer.getStreetAddress().equals(demographic.getAddress()))
                    ret = true;
                if (demographicTransfer.getPhone1() != null && !demographicTransfer.getPhone1().equals(demographic.getPhone()))
                    ret = true;
                if (demographicTransfer.getPhone2() != null && !demographicTransfer.getPhone2().equals(demographic.getPhone2()))
                    ret = true;
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }
        return ret;
    }


    /**
     * Determines if a remote date is different from the local date.
     * <p>
     * This helper method implements the following logic for date comparison:
     * <ul>
     *   <li>If remote is null: not different (local can be anything)</li>
     *   <li>If remote is not null and local is null: different (missing local value)</li>
     *   <li>If both are not null: compare dates (different if not the same day)</li>
     * </ul>
     * </p>
     * <p>
     * This asymmetric comparison prioritizes the remote value - if remote has data that
     * local doesn't, it's considered different and should potentially be synced.
     * </p>
     * 
     * @param local the local Calendar date (may be null)
     * @param remote the remote Calendar date (may be null)
     * @return true if remote has a different (or additional) date value, false otherwise
     */
    /*
    local remote
    null  null   = false
    value null   = false
    null  value  = true
    value value  = compare
    */
    public static boolean isRemoteDateDifferent(Calendar local, Calendar remote) {
        boolean isRemoteDateDifferent = false;
        if (remote != null) {
            if (local == null) {
                // Remote has a value but local doesn't - they're different
                isRemoteDateDifferent = true;
            } else if (!(DateUtils.getNumberOfDaysBetweenTwoDates(local, remote) == 0)) {
                // Both have values but they're different dates
                isRemoteDateDifferent = true;
            }
        }
        // If remote is null, we don't care what local is - not different
        return isRemoteDateDifferent;
    }


}
