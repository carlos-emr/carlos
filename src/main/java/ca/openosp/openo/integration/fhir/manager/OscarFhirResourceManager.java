//CHECKSTYLE:OFF
package ca.openosp.openo.integration.fhir.manager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import ca.openosp.openo.integration.fhir.model.Patient;
import org.hl7.fhir.dstu3.model.Identifier;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.commn.model.LookupList;
import ca.openosp.openo.commn.model.LookupListItem;
import ca.openosp.openo.commn.model.Prevention;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.integration.fhir.model.Immunization;
import ca.openosp.openo.integration.fhir.model.AbstractOscarFhirResource;
import ca.openosp.openo.integration.fhir.model.PerformingPractitioner;
import ca.openosp.openo.integration.fhir.model.Practitioner;
import ca.openosp.openo.integration.fhir.resources.types.PublicHealthUnitType;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.LookupListManager;
import ca.openosp.openo.managers.PreventionManager;
import ca.openosp.openo.managers.ProviderManager2;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.stereotype.Service;
import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;

@Service
public class OscarFhirResourceManager {

    /**
     * Gets immunizations for a patient by demographic number.
     *
     * @param configurationManager the FHIR configuration manager
     * @param demographicNo the patient's demographic ID
     * @return list of immunization records for the patient
     */
    public static final List<Immunization<Prevention>> getImmunizationsByDemographicNo(OscarFhirConfigurationManager configurationManager, int demographicNo) {
        PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);

        //TODO what kind of sec check goes here?

        List<Immunization<Prevention>> immunizations = null;
        List<Prevention> preventions = preventionManager.getPreventionsByDemographicNo(configurationManager.getLoggedInInfo(), demographicNo);

        if (preventions != null) {
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getImmunizationsByDemographicNo", "Retrieved Immunization list for FHIR transport ");

            for (Prevention prevention : preventions) {

                //TODO there needs to be a better method to identify an ISPA Immunization.  This "isImmunization" method can be changed
                if (prevention.isImmunization()) {

                    if (immunizations == null) {
                        immunizations = new ArrayList<Immunization<Prevention>>();
                    }
                    Immunization<Prevention> immunization = new Immunization<Prevention>(prevention, configurationManager);
                    immunizations.add(immunization);
                }
            }
        }

        return immunizations;
    }

    /**
     * @param configurationManager
     * @param preventionId
     * @return ca.openosp.openo.integration.fhir.model.Immunization<Prevention>
     */
    public static final Immunization<Prevention> getImmunizationById(OscarFhirConfigurationManager configurationManager, int preventionId) {
        PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);
        Prevention prevention = preventionManager.getPrevention(configurationManager.getLoggedInInfo(), preventionId);

        Immunization<Prevention> immunization = null;

        if (prevention != null) {
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getImmunizationsByDemographicNo", "Retrieved Immunization list for FHIR transport ");
            immunization = new Immunization<Prevention>(prevention, configurationManager);
        }

        return immunization;
    }

    /**
     * @param configurationManager
     * @param demographic_no
     * @return ca.openosp.openo.integration.fhir.model.Patient
     */
    public static final Patient getPatientByDemographicNumber(OscarFhirConfigurationManager configurationManager, int demographic_no) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

        Demographic demographic = demographicManager.getDemographic(configurationManager.getLoggedInInfo(), demographic_no);
        Patient patient = null;

        if (demographic != null) {
            patient = new Patient(demographic, configurationManager);
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getPatientByDemographicNumber", "Retrieved demographic " + demographic_no + " " + patient.toString());
        }

        return patient;
    }

    /**
     * Gets patients by personal health number (PHN).
     *
     * @param configurationManager the FHIR configuration manager
     * @param hcn the health card number
     * @param hcnType the type of health card
     * @return list of patients matching the health card number
     */
    public static final List<Patient> getPatientsByPHN(OscarFhirConfigurationManager configurationManager, String hcn, String hcnType) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

        List<Demographic> demographicList = demographicManager.getActiveDemosByHealthCardNo(configurationManager.getLoggedInInfo(), hcn, hcnType);
        List<Patient> patientList = null;

        if (demographicList != null) {
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getPatientsByPHN", "Retrieved demographic hcn " + hcn + " " + demographicList.toString());

            for (Demographic demographic : demographicList) {

                if (patientList == null) {
                    patientList = new ArrayList<Patient>();
                }
                Patient patient = new Patient(demographic, configurationManager);
                patientList.add(patient);
            }
        }

        return patientList;
    }

    public static final Practitioner getDemographicMostResponsiblePractitioner(OscarFhirConfigurationManager configurationManager, int demographic_no) {
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demographic = demographicManager.getDemographic(configurationManager.getLoggedInInfo(), demographic_no);
        Provider mrp = demographic.getProvider();
        List<Provider> providerList = Collections.emptyList();
        Practitioner practitioner = null;

        if (mrp == null) {
            providerList = demographicManager.getDemographicMostResponsibleProviders(configurationManager.getLoggedInInfo(), demographic_no);
        }

        if (!providerList.isEmpty()) {
            mrp = providerList.get(0);
        }

        if (mrp != null) {
            practitioner = new Practitioner(mrp, configurationManager);
        }

        return practitioner;
    }

    /**
     * @param configurationManager
     * @param providerNo
     * @return ca.openosp.openo.integration.fhir.model.PerformingPractitioner
     */
    public static final PerformingPractitioner getPerformingPractitionerByProviderNumber(OscarFhirConfigurationManager configurationManager, String providerNo) {
        ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);

        Provider provider = providerManager.getProvider(configurationManager.getLoggedInInfo(), providerNo);
        PerformingPractitioner practitioner = null;

        if (provider != null) {
            practitioner = new PerformingPractitioner(provider, configurationManager);
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getProviderByProviderNumber", "Retrieved providers " + providerNo + " " + provider.toString());
        }

        return practitioner;
    }

    /**
     * @param configurationManager
     * @param providerNo
     * @return ca.openosp.openo.integration.fhir.model.Practitioner
     */
    public static final Practitioner getPractitionerByProviderNumber(OscarFhirConfigurationManager configurationManager, String providerNo) {
        ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);

        Provider provider = providerManager.getProvider(configurationManager.getLoggedInInfo(), providerNo);
        Practitioner practitioner = null;

        if (provider != null) {
            practitioner = new Practitioner(provider, configurationManager);
            LogAction.addLogSynchronous(configurationManager.getLoggedInInfo(), "OscarFhirResourceManager.getProviderByProviderNumber", "Retrieved providers " + providerNo + " " + provider.toString());
        }

        return practitioner;
    }

    /**
     * Builds a list of linked resources of Immunization data by patient for insertion into a message Bundle
     * The returned HashSet contains:
     * - Immunizations
     * - Patient
     * - SubmittingPractitioner
     * - PerformingPractitioner
     */
    public static final HashSet<AbstractOscarFhirResource<?, ?>> getImmunizationResourceBundle(OscarFhirConfigurationManager configurationManager, Patient patient, HashSet<AbstractOscarFhirResource<?, ?>> resourceList) {

        List<Immunization<Prevention>> immunizations = OscarFhirResourceManager.getImmunizationsByDemographicNo(configurationManager, patient.getOscarResource().getDemographicNo());
        if (immunizations != null) {
            OscarFhirResourceManager.linkPerformingPractitionerAndPatient(configurationManager, immunizations, patient, resourceList);
        }

        return resourceList;
    }

    /**
     * Builds a list of linked resources of Immunization data by patient for insertion into a message Bundle
     * The returned HashSet contains:
     * - Immunizations
     * - Patient
     * - SubmittingPractitioner
     * - PerformingPractitioner
     */
    public static final HashSet<AbstractOscarFhirResource<?, ?>> getImmunizationResourceBundle(OscarFhirConfigurationManager configurationManager, Patient patient, int preventionId, HashSet<AbstractOscarFhirResource<?, ?>> resourceList) {

        Immunization<Prevention> immunization = OscarFhirResourceManager.getImmunizationById(configurationManager, preventionId);
        if (immunization != null) {
            OscarFhirResourceManager.linkPerformingPractitionerAndPatient(configurationManager, immunization, patient, resourceList);
        } else {
            MiscUtils.getLogger().warn("Requested Immunization id " + preventionId + " was not found.");
        }
        return resourceList;
    }

    /**
     * @param configurationManager
     * @param demographicNo
     * @return org.hl7.fhir.dstu3.model.Organization
     */
    public static final org.hl7.fhir.dstu3.model.Organization getPublicHealthUnit(OscarFhirConfigurationManager configurationManager, int demographicNo) {

        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        DemographicExt demographicExt = demographicManager.getDemographicExt(configurationManager.getLoggedInInfo(), demographicNo, DemographicExt.DemographicProperty.PHU);
        String phuId = null;
        org.hl7.fhir.dstu3.model.Organization organization = null;

        if (demographicExt != null) {
            phuId = demographicExt.getValue();
        }

        PublicHealthUnitType publicHealthUnitType = getPublicHealthUnitType(configurationManager, phuId);

        if (publicHealthUnitType != null) {
            organization = new org.hl7.fhir.dstu3.model.Organization();
            organization.setId(UUID.randomUUID().toString());
            Identifier identifier = new Identifier();
            identifier.setSystem(publicHealthUnitType.getSystemURI()).setValue(publicHealthUnitType.getId());
            organization.addIdentifier(identifier);
            organization.setName(publicHealthUnitType.getName());
        }

        return organization;
    }


    /******* PRIVATE HELPER METHODS BELOW THIS LINE *******/


    /**
     * Helper method intended for use from inside the class.
     *
     * @param configurationManager
     * @param immunization
     * @param patient
     * @param resourceList
     * @return HashSet<OscarFhirResource < ?, ?>>
     */
    private static final HashSet<AbstractOscarFhirResource<?, ?>> linkPerformingPractitionerAndPatient(
        OscarFhirConfigurationManager configurationManager,
        Immunization<Prevention> immunization,
        Patient patient, HashSet<AbstractOscarFhirResource<?, ?>> resourceList) {

        String performingProviderNo = immunization.getOscarResource().getProviderNo();
        if (performingProviderNo != null && !"-1".equals(performingProviderNo)) {
            PerformingPractitioner performingPractitioner = OscarFhirResourceManager.getPerformingPractitionerByProviderNumber(configurationManager, performingProviderNo);
            if (performingPractitioner != null) {
                immunization.addPerformingPractitioner(performingPractitioner.getReference());
                resourceList.add(performingPractitioner);
            }
        } else if (performingProviderNo != null && "-1".equals(performingProviderNo)) {
            Provider provider = new Provider();
            provider.setProviderNo(UUID.randomUUID().toString().substring(0, 8));
            PerformingPractitioner performingPractitioner = new PerformingPractitioner(provider, configurationManager);
            immunization.addPerformingPractitioner(performingPractitioner.getReference());
            resourceList.add(performingPractitioner);
        }

        immunization.setPatientReference(patient.getReference());
        resourceList.add(patient);
        resourceList.add(immunization);

        return resourceList;
    }

    /**
     * Helper method. Intended for use inside the class.
     *
     * @param configurationManager
     * @param immunizations
     * @param patient
     * @param resourceList
     * @return HashSet<OscarFhirResource < ?, ?>>
     */
    private static final HashSet<AbstractOscarFhirResource<?, ?>> linkPerformingPractitionerAndPatient(
        OscarFhirConfigurationManager configurationManager,
        List<Immunization<Prevention>> immunizations,
        Patient patient, HashSet<AbstractOscarFhirResource<?, ?>> resourceList) {

        if (immunizations != null && !immunizations.isEmpty()) {
            for (Immunization<Prevention> immunization : immunizations) {
                linkPerformingPractitionerAndPatient(configurationManager, immunization, patient, resourceList);
            }
        }

        return resourceList;
    }

    /**
     * Helper method. For use inside the class.
     *
     * @param configurationManager
     * @param phuId
     * @return
     */
    private static final PublicHealthUnitType getPublicHealthUnitType(OscarFhirConfigurationManager configurationManager, String phuId) {

        PublicHealthUnitType publicHealthUnitType = null;
        LookupListItem lookupListItem = null;

        if (phuId == null || phuId.isEmpty()) {
            phuId = OscarProperties.getInstance().getProperty(PublicHealthUnitType.PhuKey.default_phu.name(), null);
        }

        LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
        LookupList lookupList = lookupListManager.findLookupListByName(configurationManager.getLoggedInInfo(), PublicHealthUnitType.PhuKey.phu.name());

        if (lookupList != null) {
            lookupListItem = lookupListManager.findLookupListItemByLookupListIdAndValue(configurationManager.getLoggedInInfo(), lookupList.getId(), phuId);
        }

        if (lookupListItem != null) {
            publicHealthUnitType = new PublicHealthUnitType(lookupListItem.getValue(), lookupListItem.getLabel());

            // TODO inject the system URI from the configuration manager.

            publicHealthUnitType.setSystemURI("https://ehealthontario.ca/API/FHIR/NamingSystem/ca-on-panorama-phu-id");
        }

        return publicHealthUnitType;
    }

}
