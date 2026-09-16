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

import io.github.carlos_emr.carlos.commn.dao.ConsultRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.model.ContactSpecialty;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExt.DemographicProperty;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
    private ConsultRequestDao mockConsultRequestDao;

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
    private ProfessionalSpecialistDao mockProfessionalSpecialistDao;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private ContactSpecialtyDao mockContactSpecialtyDao;

    private EctConsultationFormRequestUtil consultationFormRequestUtil;

    @BeforeEach
    void setUp() {
        // EctConsultationFormRequestUtil's field initializers call SpringUtils.getBean(...)
        // for every collaborator — register them all before constructing it.
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(ConsultRequestDao.class, mockConsultRequestDao);
        registerMock(ConsultationRequestExtDao.class, mockConsultationRequestExtDao);
        registerMock(ConsultationServiceDao.class, mockConsultationServiceDao);
        registerMock(ContactDao.class, mockContactDao);
        registerMock(ContactSpecialtyDao.class, mockContactSpecialtyDao);
        registerMock(FaxJobDao.class, mockFaxJobDao);
        registerMock(FaxClientLogDao.class, mockFaxClientLogDao);
        registerMock(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
        consultationFormRequestUtil = new EctConsultationFormRequestUtil();
    }

    @Test
    void shouldMapHealthCareTeamRole_byServiceDescription() {
        consultationFormRequestUtil.setService("7");
        ConsultationServices service = new ConsultationServices();
        service.setServiceDesc("Cardiology");
        ContactSpecialty specialty = new ContactSpecialty();
        specialty.setId(42);
        when(mockConsultationServiceDao.find(7)).thenReturn(service);
        when(mockContactSpecialtyDao.findBySpecialty("Cardiology")).thenReturn(specialty);

        assertThat(consultationFormRequestUtil.getHealthCareTeamRole()).isEqualTo("42");
        verify(mockContactSpecialtyDao, never()).findBySpecialty("7");
        verify(mockContactSpecialtyDao, never()).findBySpecialty("other");
    }

    @Test
    void shouldUseOtherSpecialty_whenConsultationServiceHasNoCatalogueMatch() {
        consultationFormRequestUtil.setService("7");
        ConsultationServices service = new ConsultationServices();
        service.setServiceDesc("Unmapped specialty");
        ContactSpecialty fallback = new ContactSpecialty();
        fallback.setId(81);
        when(mockConsultationServiceDao.find(7)).thenReturn(service);
        when(mockContactSpecialtyDao.findBySpecialty("Unmapped specialty")).thenReturn(null);
        when(mockContactSpecialtyDao.findBySpecialty("other")).thenReturn(fallback);

        assertThat(consultationFormRequestUtil.getHealthCareTeamRole()).isEqualTo("81");
    }

    @Test
    void shouldUseUnspecifiedRole_whenServiceAndOtherSpecialtyAreMissing() {
        consultationFormRequestUtil.setService("7");
        assertThat(consultationFormRequestUtil.getHealthCareTeamRole()).isEqualTo("0");
    }

    @Test
    void shouldUseUnspecifiedRole_whenNeitherSpecialtyNorFallbackExists() {
        consultationFormRequestUtil.setService("7");
        ConsultationServices service = new ConsultationServices();
        service.setServiceDesc("Unmapped specialty");
        when(mockConsultationServiceDao.find(7)).thenReturn(service);

        assertThat(consultationFormRequestUtil.getHealthCareTeamRole()).isEqualTo("0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "invalid", "2147483648"})
    void shouldResolveUnspecifiedHealthCareTeamRole_withAbsentOrInvalidService(String service) {
        consultationFormRequestUtil.setService(service);
        assertThat(consultationFormRequestUtil.getHealthCareTeamRole()).isEqualTo("0");
        verifyNoInteractions(mockConsultationServiceDao);
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

        when(mockDemographicManager.getDemographic(mockLoggedInInfo, 123)).thenReturn(demographic);
        when(mockDemographicManager.getDemographicExt(eq(mockLoggedInInfo), eq(123), eq(DemographicProperty.demo_cell)))
                .thenReturn(demographicExt);

        boolean patientFound = consultationFormRequestUtil.estPatient(mockLoggedInInfo, "123");

        assertThat(patientFound).isTrue();
        assertThat(consultationFormRequestUtil.getPatientAddress())
                .isEqualTo("12 &lt;Main&gt;\nToronto,ON\nA1A 1A1");
    }

    @Test
    @DisplayName("should load request with associations when estimating request fields")
    void shouldLoadRequestWithAssociations_whenEstimatingRequestFields() {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(123);
        request.setServiceId(10);
        request.setPatientWillBook(false);

        Demographic demographic = new Demographic();
        demographic.setFirstName("John");
        demographic.setLastName("Doe");

        when(mockConsultRequestDao.findWithAssociations(456)).thenReturn(request);
        when(mockDemographicManager.getDemographic(mockLoggedInInfo, 123)).thenReturn(demographic);
        when(mockFaxClientLogDao.findClientLogbyRequestId(456)).thenReturn(Collections.emptyList());

        boolean requestFound = consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, "456");

        assertThat(requestFound).isTrue();
        verify(mockConsultRequestDao).findWithAssociations(456);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"invalid", "2147483648", "0", "-1"})
    void shouldRefuseInvalidRequestId_beforeQueryingCollaborators(String id) {
        assertThat(consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, id)).isFalse();
        org.mockito.Mockito.verifyNoInteractions(mockConsultRequestDao, mockProfessionalSpecialistDao);
    }

    @Test
    void shouldLoadWithoutContact_whenHealthCareTeamEnabled() {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        String key = "ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS";
        String previous = properties.getProperty(key);
        properties.setProperty(key, "true");
        try {
            stubRequestForForm();
            assertThat(consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, "456")).isTrue();
        } finally {
            if (previous == null) properties.remove(key); else properties.setProperty(key, previous);
        }
    }

    @Test
    void shouldUseRequestSpecialistContactDetails_whenRequestAndSpecialistIdsDiffer() {
        ConsultationRequest request = stubRequestForForm();
        var specialist = new io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist();
        org.springframework.test.util.ReflectionTestUtils.setField(specialist, "id", 37);
        specialist.setPhoneNumber("555-0101");
        specialist.setFaxNumber("555-0102");
        specialist.setStreetAddress("Synthetic address");
        specialist.setEmailAddress("specialist@example.test");
        request.setProfessionalSpecialist(specialist);
        assertThat(consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, "456")).isTrue();
        assertThat(consultationFormRequestUtil.getSpecPhone()).isEqualTo("555-0101");
        assertThat(consultationFormRequestUtil.getSpecFax()).isEqualTo("555-0102");
        assertThat(consultationFormRequestUtil.getSpecAddr()).isEqualTo("Synthetic address");
        assertThat(consultationFormRequestUtil.getSpecEmail()).isEqualTo("specialist@example.test");
        org.mockito.Mockito.verifyNoInteractions(mockProfessionalSpecialistDao);
    }

    @Test
    void shouldReportMissingRequest_withoutReadingSpecialist() {
        assertThat(consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, "789")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(mockProfessionalSpecialistDao);
    }

    private ConsultationRequest stubRequestForForm() {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(123);
        request.setServiceId(10);
        Demographic demographic = new Demographic();
        demographic.setFirstName("Synthetic");
        demographic.setLastName("Patient");
        when(mockConsultRequestDao.findWithAssociations(456)).thenReturn(request);
        org.mockito.Mockito.lenient().when(mockDemographicManager.getDemographic(mockLoggedInInfo, 123)).thenReturn(demographic);
        org.mockito.Mockito.lenient().when(mockFaxClientLogDao.findClientLogbyRequestId(456)).thenReturn(Collections.emptyList());
        return request;
    }
}
