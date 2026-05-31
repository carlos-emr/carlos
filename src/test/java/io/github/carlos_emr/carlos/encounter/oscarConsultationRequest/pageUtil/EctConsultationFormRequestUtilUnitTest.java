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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    private ProfessionalSpecialistDao mockProfessionalSpecialistDao;

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
        registerMock(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
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
    @DisplayName("should load request with associations when estimating request fields")
    void shouldLoadRequestWithAssociations_whenEstimatingRequestFields() {
        ConsultationRequest request = new ConsultationRequest();
        request.setDemographicId(123);
        request.setServiceId(10);
        request.setPatientWillBook(false);

        Demographic demographic = new Demographic();
        demographic.setFirstName("John");
        demographic.setLastName("Doe");

        when(mockConsultationRequestDao.findWithAssociations(456)).thenReturn(request);
        when(mockDemographicManager.getDemographic(eq(mockLoggedInInfo), eq(123))).thenReturn(demographic);
        when(mockFaxClientLogDao.findClientLogbyRequestId(456)).thenReturn(Collections.emptyList());

        boolean requestFound = consultationFormRequestUtil.estRequestFromId(mockLoggedInInfo, "456");

        assertThat(requestFound).isTrue();
        verify(mockConsultationRequestDao).findWithAssociations(456);
    }
}
