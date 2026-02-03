/**
 * Copyright (C) 2011-2012  PeaceWorks Technology Solutions
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


package io.github.carlos_emr.carlos.rx.erx;

import java.net.MalformedURLException;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.rx.erx.controller.ERxCommunicator;
import io.github.carlos_emr.carlos.rx.erx.controller.ERxPatientRecordTranslator;
import io.github.carlos_emr.carlos.rx.erx.interfaces.PatientRecordSynchronizer;
import io.github.carlos_emr.carlos.rx.erx.model.ERxApplicationPreferences;
import io.github.carlos_emr.carlos.rx.erx.model.ERxDoctorPreferences;
import io.github.carlos_emr.carlos.rx.erx.model.ERxFacilityPreferences;
import io.github.carlos_emr.carlos.rx.erx.model.ERxPatientData;
import io.github.carlos_emr.carlos.rx.erx.model.request.Transaction3;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * An object to manage sending patient data to the remote prescription providers.
 */
public class ERxPatientRecordSynchronizer implements PatientRecordSynchronizer {
    /**
     * A logger that we will use to record info and errors.
     */
    private static Logger logger = MiscUtils.getLogger();

    /**
     * Create an instance of a ERxPatientRecordSynchronizer object.
     */
    public ERxPatientRecordSynchronizer() {
        super();
    }

    /**
     * Send a patient's data to the remote prescription providers.
     *
     * @param patient    The patient data to send to the remote prescription providers.
     * @param providerId The OSCAR ID of the providers who the patient is seeing and who
     *                   may prescribe something for the patient using the external
     *                   prescription providers.
     */
    @Override
    public void sendRecord(Demographic patient, String providerId) {
        // Information about the application
        ERxApplicationPreferences oscarInfo = ERxApplicationPreferences
                .getInstance();
        /*
         * The credentials of the facility who administers the medical records
         * of the patient
         *
         * FUTURE: this should get credential objects from a factory class that
         * knows the providers's preferred external prescription providers; where
         * the credential objects returned conform to the signature of
         * ERxFacilityPreferences.
         */
        ERxFacilityPreferences facilityCredentials;

        /*
         * The credentials of the doctor who will be seeing (and possibly
         * prescribing-something-to) the patient
         *
         * FUTURE: this should get credential objects from a factory class that
         * knows the providers's preferred external prescription providers; where
         * the credential objects returned conform to the signature of
         * ERxDoctorPreferences.
         */
        ERxDoctorPreferences doctorCredentials;

        /*
         * The object which will facilitate communications
         *
         * FUTURE: this should get communicator objects from a factory class
         * that knows the providers's preferred external prescription providers;
         * where the communicator objects returned conform to the signature of
         * ERxCommunicator.
         */
        ERxCommunicator communicator;

        // Store the translated patient
        ERxPatientData translatedPatient;
        Date patientLastModifiedDate;

        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

        try {
            // Log what we're doing
            logger.info("Attempting to send a patient record to an external prescription providers.");

            // Get the credentials we will use for communicating
            doctorCredentials = ERxDoctorPreferences.getInstance(providerId);

            facilityCredentials = ERxFacilityPreferences.getInstance();

            // Create a communicator to facilitate communications
            communicator = new ERxCommunicator(
                    doctorCredentials.getConnectionURL(),
                    doctorCredentials.getUsername(),
                    doctorCredentials.getPassword(),
                    facilityCredentials.getLocale());

            try {

                // Translate the patient to send
                translatedPatient = ERxPatientRecordTranslator
                        .translateToExternal(patient);
                patientLastModifiedDate = demographicDao.getDemographicById(
                        patient.getDemographicNo()).getLastUpdateDate();

                try {
                    // Create a request and send it
                    communicator.sendPatientData(
                            translatedPatient,
                            doctorCredentials.getClientNumber(),
                            doctorCredentials.isInTrainingMode(),
                            new Transaction3(ERxFacilityPreferences
                                    .getNextTransactionId(), oscarInfo
                                    .getSoftwareName(), oscarInfo.getVendor(),
                                    oscarInfo.getVersion(),
                                    patientLastModifiedDate));
                } catch (SecurityException e) {
                    logger.error("Failed to send a patient record to an external prescription providers because the remote www service denied us access: "
                            + e.getMessage()
                            + " This is likely due to incorrectly-configured providers or facility credentials.", e);
                }
            } catch (NumberFormatException e) {
                logger.error("Failed to translate a patient because an unparsable number was given: "
                        + e.getMessage(), e);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Failed to send a patient record to an external prescription providers because the doctor ID given was not valid, or did not have any external prescription providers data stored.", e);
        } catch (MalformedURLException e) {
            logger.error("Failed to send a patient record to an external prescription providers because the www service URL was not valid.", e);
        } finally {
            logger.info("Completed attempt to send a patient record to an external prescription providers.");
        }
    }
}
