/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.integration.fhir.builder;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.model.Immunization;
import io.github.carlos_emr.carlos.integration.fhir.model.AbstractOscarFhirResource;
import io.github.carlos_emr.carlos.integration.fhir.model.Patient;
import io.github.carlos_emr.carlos.integration.fhir.model.PerformingPractitioner;
import io.github.carlos_emr.carlos.integration.fhir.model.SubmittingPractitioner;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.FhirDestination;
import io.github.carlos_emr.carlos.integration.fhir.resources.constants.Region;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Integration tests for {@link FhirBundleBuilder}, verifying DHIR-formatted FHIR
 * Bundle message generation with immunization resources.
 *
 * <p>Migrated from legacy JUnit 4 {@code FhirMessageBuilderTest}. The original
 * BIS-formatted message test was already commented out due to SenderFactory
 * static initialization issues.</p>
 *
 * <p>Uses a class-level MockedStatic for SpringUtils because {@link SenderFactory}
 * calls {@code SpringUtils.getBean(ClinicDAO.class)} in a static field initializer.
 * The mock must be active before the class is loaded and persist for all tests
 * (static initializers only run once per classloader).</p>
 *
 * @see FhirBundleBuilder
 * @see OscarFhirConfigurationManager
 * @since 2017-01-01
 */
@Tag("integration")
@Tag("fhir")
@DisplayName("FHIR Message Builder Integration Tests")
class FhirMessageBuilderIntegrationTest {

    private static MockedStatic<SpringUtils> springUtilsMock;

    private Clinic clinic;
    private Provider provider;
    private Provider nurse;
    private Provider doctor;
    private Demographic demographic;
    private Prevention prevention;
    private Prevention prevention2;

    @BeforeAll
    static void setUpSpringUtilsMock() {
        springUtilsMock = Mockito.mockStatic(SpringUtils.class);
        ClinicDAO mockClinicDao = Mockito.mock(ClinicDAO.class);
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class)))
            .thenReturn(null);
        springUtilsMock.when(() -> SpringUtils.getBean(ClinicDAO.class))
            .thenReturn(mockClinicDao);
    }

    @AfterAll
    static void tearDownSpringUtilsMock() {
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    @BeforeEach
    void setUp() {
        // SENDER
        clinic = new Clinic();
        clinic.setId(4321);
        clinic.setClinicAddress("123 Clinic Street");
        clinic.setClinicCity("Vancouver");
        clinic.setClinicProvince("BC");
        clinic.setClinicPhone("778-567-3445");
        clinic.setClinicFax("778-343-3453");
        clinic.setClinicName("Test Medical Clinic");

        // IMMUNIZATION
        prevention = new Prevention();
        prevention.setImmunizationDate(new Date(System.currentTimeMillis()));
        prevention.setImmunizationRefused(Boolean.FALSE);
        prevention.setComment("This is a comment");
        prevention.setVaccineCode("SM1234527");
        prevention.setDose("10cc");
        prevention.setImmunizationType("T");
        prevention.setSite("LD");
        prevention.setRoute("IM");
        prevention.setLotNo("667234");
        prevention.setManufacture("Pfizer");
        prevention.setName("Tetanus Vaccine");

        prevention2 = new Prevention();
        prevention2.setImmunizationDate(new Date(System.currentTimeMillis()));
        prevention2.setImmunizationRefused(Boolean.FALSE);
        prevention2.setImmunizationRefusedReason("Didnt want it.");
        prevention2.setComment("This is a comment");
        prevention2.setVaccineCode("SM4567445527");
        prevention2.setDose("20cc");
        prevention2.setImmunizationType("HPV");
        prevention2.setSite("LD");
        prevention2.setRoute("IM");
        prevention2.setLotNo("123456");
        prevention2.setManufacture("Pfizer");
        prevention2.setName("HPV Vaccine");

        // PATIENT
        demographic = new Demographic();
        demographic.setDemographicNo(122343);
        demographic.setTitle("Mr");
        demographic.setSex("M");
        demographic.setFirstName("Dennis");
        demographic.setLastName("Warren");
        demographic.setAddress("123 Abc Street");
        demographic.setCity("Vancouver");
        demographic.setProvince("BC");
        demographic.setPostal("V6E4G7");
        demographic.setPhone("604-555-1212");
        demographic.setPhone2("604-555-5555");
        demographic.setHin("9876446854");
        demographic.setSpokenLanguage("English");
        Calendar birthdate = Calendar.getInstance();
        birthdate.set(1969, 6, 18);
        demographic.setBirthDay(birthdate);

        // PRACTITIONER
        provider = new Provider();
        provider.setProviderNo("8879");
        provider.setFirstName("Doug");
        provider.setLastName("Ross");
        provider.setPractitionerNo("12342");
        provider.setHsoNo("12342");
        provider.setOhipNo("12342");
        provider.setPhone("604-290-2343");
        provider.setWorkPhone("604-333-2343");
        provider.setPractitionerNoType("CPSO");

        nurse = new Provider();
        nurse.setProviderNo("6768");
        nurse.setFirstName("Nurse");
        nurse.setLastName("Betty");
        nurse.setPractitionerNo("345645");
        nurse.setPhone("645-290-1235");
        nurse.setWorkPhone("604-333-2343");
        nurse.setPractitionerNoType("CNO");

        doctor = new Provider();
        doctor.setProviderNo("6433");
        doctor.setFirstName("Doctor");
        doctor.setLastName("Sharp");
        doctor.setPractitionerNo("457888");
        doctor.setHsoNo("12342");
        doctor.setOhipNo("12342");
        doctor.setPhone("604-333-2343");
        doctor.setWorkPhone("604-333-2343");
        doctor.setPractitionerNoType("CPSO");
    }

    @AfterEach
    void tearDown() {
        clinic = null;
        provider = null;
        demographic = null;
        prevention = null;
    }

    @Nested
    @DisplayName("DHIR formatted FHIR Bundle generation")
    class DhirBundleGeneration {

        private FhirBundleBuilder fhirBundleBuilder;
        private String json;

        @BeforeEach
        void buildBundle() {
            LoggedInInfo loggedInInfo = new LoggedInInfo();
            Security security = new Security();
            security.setOneIdEmail("oneid@oneidemail.com");
            loggedInInfo.setLoggedInProvider(provider);
            loggedInInfo.setLoggedInSecurity(security);

            Settings settings = new Settings(FhirDestination.DHIR, Region.ON);
            settings.setIncludeSenderEndpoint(Boolean.FALSE);

            OscarFhirConfigurationManager configurationManager = new OscarFhirConfigurationManager(loggedInInfo, settings);

            Patient patient = new Patient(demographic, configurationManager);
            patient.setFocusResource(Boolean.TRUE);

            PerformingPractitioner performing = new PerformingPractitioner(provider, configurationManager);
            PerformingPractitioner performing2 = new PerformingPractitioner(provider, configurationManager);
            SubmittingPractitioner submitting = new SubmittingPractitioner(provider, configurationManager);

            Immunization<Prevention> measles = new Immunization<Prevention>(prevention, configurationManager);
            Immunization<Prevention> hpv = new Immunization<Prevention>(prevention2, configurationManager);

            fhirBundleBuilder = new FhirBundleBuilder(configurationManager);

            measles.getFhirResource().setPatient(patient.getReference());
            measles.addPerformingPractitioner(performing.getReference());

            hpv.getFhirResource().setPatient(patient.getReference());
            hpv.addPerformingPractitioner(performing2.getReference());

            HashSet<AbstractOscarFhirResource<?, ?>> resourceList = new HashSet<AbstractOscarFhirResource<?, ?>>();
            resourceList.add(patient);
            resourceList.add(performing);
            resourceList.add(performing2);
            resourceList.add(submitting);
            resourceList.add(measles);
            resourceList.add(hpv);

            fhirBundleBuilder.addResources(resourceList);

            json = fhirBundleBuilder.getMessageJson();
        }

        @Test
        @DisplayName("should generate non-empty JSON output")
        void shouldGenerateNonEmptyJson() {
            assertThat(json).isNotNull();
            assertThat(json).isNotEmpty();
        }

        @Test
        @DisplayName("should contain Bundle resource type in JSON")
        void shouldContainBundleResourceType_inJson() {
            assertThat(json).contains("\"resourceType\" : \"Bundle\"");
        }

        @Test
        @DisplayName("should contain MessageHeader as first entry")
        void shouldContainMessageHeader_asFirstEntry() {
            assertThat(json).contains("\"resourceType\" : \"MessageHeader\"");
        }

        @Test
        @DisplayName("should contain patient demographic data in JSON")
        void shouldContainPatientDemographicData_inJson() {
            assertThat(json).contains("Dennis");
            assertThat(json).contains("Warren");
        }

        @Test
        @DisplayName("should contain immunization resources in JSON")
        void shouldContainImmunizationResources_inJson() {
            assertThat(json).contains("\"resourceType\" : \"Immunization\"");
        }

        @Test
        @DisplayName("should contain practitioner resources in JSON")
        void shouldContainPractitionerResources_inJson() {
            assertThat(json).contains("\"resourceType\" : \"Practitioner\"");
        }

        @Test
        @DisplayName("should create bundle with message type")
        void shouldCreateBundle_withMessageType() {
            Bundle bundle = fhirBundleBuilder.getBundle();
            assertThat(bundle).isNotNull();
            assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.MESSAGE);
        }

        @Test
        @DisplayName("should create bundle with UUID identifier")
        void shouldCreateBundle_withUuidIdentifier() {
            Bundle bundle = fhirBundleBuilder.getBundle();
            assertThat(bundle.getId()).isNotNull();
            assertThat(bundle.getId()).isNotEmpty();
        }

        @Test
        @DisplayName("should contain expected number of bundle entries")
        void shouldContainExpectedNumberOfBundleEntries() {
            Bundle bundle = fhirBundleBuilder.getBundle();
            List<BundleEntryComponent> entries = bundle.getEntry();
            // 1 MessageHeader + 1 Patient + 2 PerformingPractitioner + 1 SubmittingPractitioner + 2 Immunization = 7
            assertThat(entries).hasSizeGreaterThanOrEqualTo(7);
        }

        @Test
        @DisplayName("should set message header event system for medication administration")
        void shouldSetMessageHeaderEventSystem_forMedicationAdministration() {
            assertThat(fhirBundleBuilder.getMessageHeader()).isNotNull();
            assertThat(fhirBundleBuilder.getMessageHeader().getEvent().getSystem())
                    .isEqualTo("http://hl7.org/fhir/message-events");
            assertThat(fhirBundleBuilder.getMessageHeader().getEvent().getCode())
                    .isEqualTo("MedicationAdministration-Recording");
        }
    }
}
