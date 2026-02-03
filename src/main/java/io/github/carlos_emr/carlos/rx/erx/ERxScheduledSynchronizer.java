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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.rx.erx;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.rx.erx.controller.ERxChartUpdater;
import io.github.carlos_emr.carlos.rx.erx.controller.ERxCommunicator;
import io.github.carlos_emr.carlos.rx.erx.controller.ERxPrescriptionTranslator;
import io.github.carlos_emr.carlos.rx.erx.model.ERxFacilityPreferences;
import io.github.carlos_emr.carlos.rx.erx.model.ERxPrescription;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;

/**
 * An object to manage the periodic tasks that the OscarERx plugin needs to do.
 */
public class ERxScheduledSynchronizer {
    /**
     * A logger that we will use to record info and errors.
     */
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Periodically check external prescription providers for prescriptions
     * issued through them, and synchronize them to OSCAR.
     */
    public static void syncPrescriptions(Date dateToSync) {
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
         * The object which will facilitate communications
         *
         * FUTURE: this should get communicator objects from a factory class
         * that knows the providers's preferred external prescription providers;
         * where the communicator objects returned conform to the signature of
         * ERxCommunicator.
         */
        ERxCommunicator communicator;

        // Store untranslated prescriptions
        List<ERxPrescription> untranslatedPrescriptions;
        Iterator<ERxPrescription> untranslatedPrescriptionIterator;

        // Store translated prescriptions
        List<Drug> translatedPrescriptions = new LinkedList<Drug>();
        Iterator<Drug> translatedPrescriptionIterator;

        // If the date to sync is null somehow, sync today
        if (dateToSync == null) {
            dateToSync = new Date();
        }

        try {
            // Log what we're doing
            logger.info("Attempting to request a list of prescriptions that have changed from external prescription providers(s).");

            // Get the credentials we will use for communicating
            facilityCredentials = ERxFacilityPreferences.getInstance();

            // Create a communicator to facilitate communications
            communicator = new ERxCommunicator(
                    facilityCredentials.getRemoteURL(),
                    facilityCredentials.getUsername(),
                    facilityCredentials.getPassword(),
                    facilityCredentials.getLocale());

            try {
                ProviderData issuingProvider;
                Demographic patient;
                ERxPrescription toTranslate;
                String drugATCCode;

                RxDrugRef drugLookup = new RxDrugRef();

                DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
                ProviderDataDao providerDataDao = (ProviderDataDao) SpringUtils.getBean(ProviderDataDao.class);

                // Construct a request and send it
                untranslatedPrescriptions = communicator
                        .requestPrescriptionData(Integer
                                        .toString(facilityCredentials.getFacilityId()),
                                "", dateToSync);
                if (untranslatedPrescriptions != null) {
                    logger.debug("untranslatedPrescriptions size :" + untranslatedPrescriptions.size());
                } else {
                    logger.debug("untranslatedPrescriptions was null");
                }
                // Translate each prescription returned
                untranslatedPrescriptionIterator = untranslatedPrescriptions
                        .iterator();

                while (untranslatedPrescriptionIterator.hasNext()) {
                    try {
                        toTranslate = untranslatedPrescriptionIterator.next();

                        // Retrieve the doctor's information
                        issuingProvider = providerDataDao.findByOhipNumber(toTranslate
                                .getDoctorLicenseNo());
                        if (issuingProvider == null) {
                            throw new IllegalArgumentException(
                                    "Prescription to parse contains an invalid doctor license number "
                                            + toTranslate.getDoctorLicenseNo());
                        }

                        // Retrieve patient information
                        patient = demographicDao.getDemographic(toTranslate.getPatientId());
                        if (patient == null) {
                            throw new IllegalArgumentException(
                                    "Prescription to parse contains an invalid patient ID number "
                                            + toTranslate.getPatientId());
                        }

                        // Retrieve drug information
                        drugATCCode = (String) drugLookup.atcFromDIN(Long.toString(toTranslate.getDrugCode())).firstElement();

                        if (drugATCCode == null) {
                            throw new IllegalArgumentException(
                                    "Prescription to parse contains an unknown product DIN number "
                                            + toTranslate.getDrugCode());
                        }
                        translatedPrescriptions.add(ERxPrescriptionTranslator
                                .translateToInternal(toTranslate,
                                        issuingProvider.getId(),
                                        patient.getDemographicNo().toString(),
                                        drugATCCode));
                    } catch (IllegalArgumentException e) {
                        logger.error("Failed to translate a prescription because: "
                                + e.getMessage());
                    } catch (Exception e) {
                        logger.error("Failed to translate a prescription because the drug could not be found: "
                                + e.getMessage());
                    }
                }

                // Add each prescription to the chart
                translatedPrescriptionIterator = translatedPrescriptions
                        .iterator();
                while (translatedPrescriptionIterator.hasNext()) {
                    try {
                        ERxChartUpdater
                                .updateChartWithPrescription(translatedPrescriptionIterator
                                        .next());
                    } catch (IllegalArgumentException e) {
                        logger.error("Failed to update a patient's chart with a prescription because the following error occurred: "
                                + e.getMessage());
                    }
                }
            } catch (SecurityException e) {
                logger.error("Failed to request prescriptions from an external prescription providers because the remote www service denied us access: "
                        + e.getMessage()
                        + " This is likely due to incorrectly-configured providers or facility credentials.");
            }
        } catch (MalformedURLException e) {
            logger.error("Failed to request prescriptions from an external providers because the URL stored in the facility preferences did not validate. The URL given was: "
                    + e.getMessage());
        } finally {
            logger.info("Completed attempt to request a list of prescriptions that have changed from external prescription providers(s).");
        }
    }
}
