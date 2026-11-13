/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExt.DemographicProperty;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EctConsultationFormRequestUtil}.
 *
 * @since 2026-04-19
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EctConsultationFormRequestUtil Unit Tests")
@Tag("unit")
@Tag("consultation")
class EctConsultationFormRequestUtilUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private ConsultationRequestDao mockConsultationRequestDao;

    @Mock
    private ConsultationRequestExtDao mockConsultationRequestExtDao;

    @Mock
    private ConsultationServiceDao mockConsultationServiceDao;

    @Mock
    private ContactDao mockContactDao;

    @Mock
    private FaxJobDao mockFaxJobDao;

    @Mock
    private FaxClientLogDao mockFaxClientLogDao;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private EctConsultationFormRequestUtil consultationFormRequestUtil;

    @BeforeEach
    void setUp() {
        // EctConsultationFormRequestUtil's field initializers call SpringUtils.getBean(...)
        // for every collaborator — register them all before constructing it.
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(ConsultationRequestDao.class, mockConsultationRequestDao);
        registerMock(ConsultationRequestExtDao.class, mockConsultationRequestExtDao);
        registerMock(ConsultationServiceDao.class, mockConsultationServiceDao);
        registerMock(ContactDao.class, mockContactDao);
        registerMock(FaxJobDao.class, mockFaxJobDao);
        registerMock(FaxClientLogDao.class, mockFaxClientLogDao);
        consultationFormRequestUtil = new EctConsultationFormRequestUtil();
    }

    @Test
    @DisplayName("should preserve encoded address lines when patient exists")
    void shouldPreserveEncodedAddressLines_whenPatientExists() {
        Demographic demographic = new Demographic();
        demographic.setFirstName("John");
        demographic.setLastName("Doe");
        demographic.setAddress("12 <Main>");
        demographic.setCity("Toronto");
        demographic.setProvince("ON");
        demographic.setPostal("A1A 1A1");
        demographic.setPhone("416-111-2222");
        demographic.setPhone2("416-333-4444");
        demographic.setEmail("john@example.com");
        demographic.setHin("1234567890");
        demographic.setHcType("ON");
        demographic.setVer("AB");
        demographic.setChartNo("CH-1");
        demographic.setSex("M");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");

        DemographicExt demographicExt = new DemographicExt();
        demographicExt.setValue("416-555-6666");

        when(mockDemographicManager.getDemographic(eq(mockLoggedInInfo), eq(123))).thenReturn(demographic);
        when(mockDemographicManager.getDemographicExt(eq(mockLoggedInInfo), eq(123), eq(DemographicProperty.demo_cell)))
                .thenReturn(demographicExt);

        boolean patientFound = consultationFormRequestUtil.estPatient(mockLoggedInInfo, "123");

        assertThat(patientFound).isTrue();
        assertThat(consultationFormRequestUtil.getPatientAddress())
                .isEqualTo("12 &lt;Main&gt;\nToronto,ON\nA1A 1A1");
    }

    @Test
    @DisplayName("should return the service description for a numeric id")
    void shouldReturnServiceDescription_whenIdIsNumeric() {
        ConsultationServices service = new ConsultationServices();
        service.setServiceDesc("Cardiology");
        when(mockConsultationServiceDao.find(eq(7))).thenReturn(service);

        assertThat(consultationFormRequestUtil.getServiceName(" 7 ")).isEqualTo("Cardiology");
    }

    @Test
    @DisplayName("should return empty text instead of throwing for a missing or malformed id")
    void shouldReturnEmpty_whenServiceIdMissingOrMalformed() {
        assertThat(consultationFormRequestUtil.getServiceName(null)).isEmpty();
        assertThat(consultationFormRequestUtil.getServiceName("  ")).isEmpty();
        assertThat(consultationFormRequestUtil.getServiceName("abc")).isEmpty();
        verifyNoInteractions(mockConsultationServiceDao);
    }

    @Test
    @DisplayName("should classify joined fax rows without loading jobs individually")
    void shouldClassifyFaxHistory_fromJoinedRows() {
        var logDao = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao.class);
        registerMock(io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao.class, logDao);
        var specialist = new io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist();
        specialist.setFaxNumber("(555) 123-4567");
        var util = new EctConsultationFormRequestUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(util, "professionalSpecialist", specialist);
        var main = new io.github.carlos_emr.carlos.consultation.dto.ConsultationFaxRow(
                "5551234567", "Specialist", io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS.SENT, new java.util.Date(1000L));
        var copy = new io.github.carlos_emr.carlos.consultation.dto.ConsultationFaxRow(
                "5557654321", "Copy", io.github.carlos_emr.carlos.commn.model.FaxJob.STATUS.ERROR, new java.util.Date(500L));
        org.mockito.Mockito.when(logDao.findConsultationFaxRows(42)).thenReturn(java.util.List.of(main, copy));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(util, "getFaxLogs", "42");
        org.assertj.core.api.Assertions.assertThat(util.getSpecialistFaxLog().getStatus()).isEqualTo(main.status());
        org.assertj.core.api.Assertions.assertThat(util.getSpecialistFaxLog().getSent()).isEqualTo(main.sent());
        org.assertj.core.api.Assertions.assertThat(util.getCopyToFaxLog()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(util.getCopyToFaxLog().iterator().next().getFax()).isEqualTo(copy.destination());
        org.mockito.Mockito.verify(logDao).findConsultationFaxRows(42);
        org.mockito.Mockito.verifyNoInteractions(mockFaxJobDao);
    }

    /** The consultation request 77 of patient 42, as the chart loads it. */
    private io.github.carlos_emr.carlos.commn.model.ConsultationRequest request(
            io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist specialist,
            io.github.carlos_emr.carlos.commn.model.DemographicContact link) {
        var request = new io.github.carlos_emr.carlos.commn.model.ConsultationRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 77);
        request.setDemographicId(42);
        request.setReferralDate(new java.util.Date(0));
        request.setProfessionalSpecialist(specialist);
        request.setDemographicContact(link);
        when(mockConsultationRequestDao.find(77)).thenReturn(request);
        return request;
    }

    /** A health-care-team link of patient 42. */
    private static io.github.carlos_emr.carlos.commn.model.DemographicContact link(
            int type, String category, String contactId) {
        var link = new io.github.carlos_emr.carlos.commn.model.DemographicContact();
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 12);
        link.setDemographicNo(42);
        link.setType(type);
        link.setCategory(category);
        link.setContactId(contactId);
        return link;
    }

    /** Runs the chart's request load with the health care team on, as its Print does. */
    private EctConsultationFormRequestUtil load(boolean demographicRead) {
        var security = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        org.mockito.Mockito.lenient()
                .when(security.hasPrivilege(org.mockito.ArgumentMatchers.any(), eq("_demographic"), eq("r"),
                        org.mockito.ArgumentMatchers.<String>isNull()))
                .thenReturn(demographicRead);
        registerMock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class, security);
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        String previous = properties.getProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS");
        properties.setProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true");
        try {
            var util = new EctConsultationFormRequestUtil();
            util.estRequestFromId(mockLoggedInInfo, "77");
            return util;
        } finally {
            if (previous == null) properties.remove("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS");
            else properties.setProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", previous);
        }
    }

    private io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao directoryWithAnUnrelatedSpecialist40() {
        // A directory specialist that happens to share the team contact's id 40.
        var directory = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao.class);
        var unrelated = new io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist();
        unrelated.setSalutation("Dr.");
        unrelated.setFirstName("Other");
        unrelated.setLastName("DIRECTORY");
        unrelated.setProfessionalLetters("MD");
        unrelated.setPhoneNumber("555-0900");
        org.mockito.Mockito.lenient().when(directory.find(40)).thenReturn(unrelated);
        registerMock(io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao.class, directory);
        return directory;
    }

    @Test
    @DisplayName("should print a health-care-team contact with its own values, never a directory row sharing its id")
    void shouldPrintTheTeamContactsOwnValues_whenADirectorySpecialistSharesItsId() {
        var directory = directoryWithAnUnrelatedSpecialist40();
        var contact = new io.github.carlos_emr.carlos.commn.model.ProfessionalContact();
        contact.setId(40);
        contact.setFirstName("Nora");
        contact.setLastName("NURSE");
        contact.setAddress("8 Care Rd");
        contact.setCity("Testville");
        contact.setProvince("ON");
        contact.setWorkPhone("555-0500");
        contact.setFax("555-0501");
        contact.setCpso("12345");
        when(mockContactDao.find((Object) 40)).thenReturn(contact);
        request(null, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_CONTACT, "Professional", "40"));

        var consultant = load(true).getProfessionalSpecialist();

        assertThat(consultant.getFormattedTitle()).isEqualTo("NURSE, Nora");
        assertThat(consultant.getPhoneNumber()).isEqualTo("555-0500");
        assertThat(consultant.getFaxNumber()).isEqualTo("555-0501");
        assertThat(consultant.getStreetAddress()).isEqualTo("8 Care Rd, Testville, ON");
        assertThat(consultant.getId()).isNull();
        org.mockito.Mockito.verify(directory, org.mockito.Mockito.never()).find(40);
    }

    @Test
    @DisplayName("should print a team contact without a phone or CPSO number, as a CDS import creates one")
    void shouldPrintBlankValues_whenTheTeamContactHasNoPhoneOrCpso() {
        directoryWithAnUnrelatedSpecialist40();
        var contact = new io.github.carlos_emr.carlos.commn.model.ProfessionalContact();
        contact.setId(40);
        contact.setFirstName("Nora");
        contact.setLastName("NURSE");
        when(mockContactDao.find((Object) 40)).thenReturn(contact);
        request(null, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_CONTACT, "professional", "40"));

        var consultant = load(true).getProfessionalSpecialist();

        assertThat(consultant.getFormattedTitle()).isEqualTo("NURSE, Nora");
        assertThat(consultant.getPhoneNumber()).isNull();
        assertThat(consultant.getFaxNumber()).isNull();
        assertThat(consultant.getStreetAddress()).isEmpty();
    }

    @Test
    @DisplayName("should print the directory specialist when the request has no team contact, or one the form never offers")
    void shouldPrintTheDirectorySpecialist_whenTheRequestHasNoProfessionalTeamContact() {
        directoryWithAnUnrelatedSpecialist40();
        var specialist = new io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist();
        specialist.setLastName("SPECIALIST");
        specialist.setFirstName("Sam");
        // A request saved before the health care team was turned on has no link at all.
        request(specialist, null);
        assertThat(load(true).getProfessionalSpecialist()).isSameAs(specialist);
        // A directory specialist on the team is the request's own specialist.
        request(specialist, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_PROFESSIONALSPECIALIST, "professional", "9001"));
        assertThat(load(true).getProfessionalSpecialist()).isSameAs(specialist);
        // A professional link to a personal Contact row, or with an unreadable id, names no consultant.
        when(mockContactDao.find((Object) 40)).thenReturn(new io.github.carlos_emr.carlos.commn.model.Contact());
        request(null, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_CONTACT, "professional", "40"));
        assertThat(load(true).getProfessionalSpecialist()).isNull();
        request(null, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_CONTACT, "professional", "forty"));
        assertThat(load(true).getProfessionalSpecialist()).isNull();
    }

    @Test
    @DisplayName("should require demographic read rights to print a team contact")
    void shouldRefuseTheTeamContact_withoutDemographicReadRights() {
        directoryWithAnUnrelatedSpecialist40();
        var contact = new io.github.carlos_emr.carlos.commn.model.ProfessionalContact();
        contact.setId(40);
        when(mockContactDao.find((Object) 40)).thenReturn(contact);
        request(null, link(io.github.carlos_emr.carlos.commn.model.DemographicContact.TYPE_CONTACT, "professional", "40"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> load(false))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
    }
}
