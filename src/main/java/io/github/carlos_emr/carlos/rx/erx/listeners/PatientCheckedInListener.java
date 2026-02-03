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

package io.github.carlos_emr.carlos.rx.erx.listeners;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.event.AppointmentStatusChangeEvent;
import io.github.carlos_emr.carlos.rx.erx.ERxPatientRecordSynchronizer;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.context.ApplicationListener;

/**
 * Listens for PatientCheckedInEvents and triggers sending patient data.
 */
public class PatientCheckedInListener implements ApplicationListener<AppointmentStatusChangeEvent> {
    Logger logger = MiscUtils.getLogger();

    /**
     * Create an instance of a PatientCheckedInListener object.
     */
    public PatientCheckedInListener() {
        super();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * io.github.carlos_emr.carlos.event.interfaces.EventListener#onOscarEvent(io.github.carlos_emr.carlos.event.interfaces.EventData)
     */
    @Override
    public void onApplicationEvent(AppointmentStatusChangeEvent event) {
        try {
            String providerId = event.getProvider_no();
            logger.debug("AppointmentStatusChange for providers " + providerId + " appt " + event.getAppointment_no() + " status is " + event.getStatus());

            ProviderPreferenceDao providerPreferenceDao = ((ProviderPreferenceDao) SpringUtils.getBean(ProviderPreferenceDao.class));

            // Load provider preferences to see if we should continue
            ProviderPreference providerPreference = providerPreferenceDao.find(providerId);
            logger.debug("proPref " + providerPreference);
            // If the providers uses an external prescription providers, and it's enabled...
            if ((providerPreference != null) && providerPreference.isERxEnabled()) {

                DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
                OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);

                // If the appointment status is "Here" or "Empty room" or "Picked"...
                String apptStatus = event.getStatus();
                if (apptStatus.equals("H") || apptStatus.equals("E") || apptStatus.equals("P")) {
                    // Get data about the appointment
                    Appointment appt = appointmentDao.find(Integer.parseInt(event.getAppointment_no()));

                    // Load the patient data from the first appointment matched
                    String patientId = String.valueOf(appt.getDemographicNo());
                    Demographic patient = demographicDao.getDemographic(patientId);
                    logger.debug("calling sendRecord");
                    // Send the patient's record
                    ERxPatientRecordSynchronizer synchronizer = new ERxPatientRecordSynchronizer();
                    synchronizer.sendRecord(patient, providerId);
                } else {
                    logger.debug("Ignoring appt event");
                }
            }
        } catch (Exception e) {
            logger.error("error", e);
        } finally {
            DbConnectionFilter.releaseAllThreadDbResources();
        }

    }//End of onOscarEvent
}
