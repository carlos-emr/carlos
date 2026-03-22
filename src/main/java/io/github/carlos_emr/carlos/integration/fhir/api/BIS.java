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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.integration.fhir.api;

import io.github.carlos_emr.carlos.integration.fhir.model.Patient;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.Organization;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.integration.fhir.builder.FhirCommunicationBuilder;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirResourceManager;
import io.github.carlos_emr.carlos.integration.fhir.model.ClinicalImpression;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.Region;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Birth Information System (BIS) API integration for FHIR-based communication.
 *
 * <p>Provides a synchronized factory method for building FHIR Communication messages
 * that bundle clinical data (Well Baby, Well Baby CSD, and Antenatal Record)
 * as Base64-encoded attachments targeted at the Ontario DHIR endpoint.</p>
 *
 * @since 2026-03-17
 */
public class BIS {

    private static Settings settings = new Settings(FhirDestination.DHIR, Region.ON);

    /**
     * Builds a FHIR Communication message containing clinical attachments for a patient.
     *
     * <p>Retrieves the patient by demographic number and attaches Well Baby, Well Baby CSD,
     * and Antenatal Record data as Base64-encoded text/plain payloads. An Organization
     * resource for the clinic is added as a contained resource.</p>
     *
     * @param loggedInInfo the logged-in user session context
     * @param demographicNo the patient's demographic ID
     * @param wbData Well Baby data to attach, or {@code null} to omit
     * @param wbCsdData Well Baby CSD data to attach, or {@code null} to omit
     * @param arData Antenatal Record data to attach, or {@code null} to omit
     * @param clinic the clinic whose Organization resource will be embedded
     * @return FhirCommunicationBuilder configured with patient subject, attachments, and organization
     */
    public static synchronized FhirCommunicationBuilder getFhirCommunicationBuilder(LoggedInInfo loggedInInfo, int demographicNo, String wbData, String wbCsdData, String arData, Clinic clinic) {

        OscarFhirConfigurationManager configurationManager = new OscarFhirConfigurationManager(loggedInInfo, settings);

        Patient patient = OscarFhirResourceManager.getPatientByDemographicNumber(configurationManager, demographicNo);

        // Practitioner
        //Practitioner practitioner = new Practitioner(providers, configurationManager);

        FhirCommunicationBuilder fhirCommunicationBuilder = new FhirCommunicationBuilder(configurationManager);
        fhirCommunicationBuilder.setSubject(patient);

        if (wbData != null) {
            ClinicalImpression clinicalImpression = new ClinicalImpression(wbData);
            clinicalImpression.setDescription("Well Baby");
            fhirCommunicationBuilder.addAttachment(clinicalImpression.copyToAttachement(new Attachment()));
        }

        if (wbCsdData != null) {
            ClinicalImpression clinicalImpression = new ClinicalImpression(wbCsdData);
            clinicalImpression.setDescription("Well Baby CSD");
            fhirCommunicationBuilder.addAttachment(clinicalImpression.copyToAttachement(new Attachment()));
        }
        if (arData != null) {
            ClinicalImpression clinicalImpression = new ClinicalImpression(arData);
            clinicalImpression.setDescription("Antenatal Record");
            fhirCommunicationBuilder.addAttachment(clinicalImpression.copyToAttachement(new Attachment()));
        }


        patient.setManagingOrganizationReference("#Organization" + clinic.getId());


        Organization organization = new org.hl7.fhir.dstu3.model.Organization();
        organization.setId("#Organization" + clinic.getId());
        organization.setName(clinic.getClinicName());
        fhirCommunicationBuilder.addResource(organization);


        return fhirCommunicationBuilder;
    }

}
