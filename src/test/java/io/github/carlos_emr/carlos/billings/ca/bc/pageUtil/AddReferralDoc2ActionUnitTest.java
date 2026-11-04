/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingreferralDao;
import io.github.carlos_emr.carlos.commn.model.Billingreferral;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddReferralDoc2Action}: the save behind BC Administration ▸ Billing ▸ Manage Referral
 * Doctors' Add and Update form.
 *
 * @since 2026-10-17
 */
@DisplayName("AddReferralDoc2Action")
@Tag("unit")
@Tag("billing")
class AddReferralDoc2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private BillingreferralDao mockBillingreferralDao;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingreferralDao.class, mockBillingreferralDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    /** The values the form posts, set the way Struts binds them. */
    private static AddReferralDoc2Action postedForm() {
        AddReferralDoc2Action action = new AddReferralDoc2Action();
        action.setReferral_no("90001");
        action.setLast_name("FAKE-Last");
        action.setFirst_name("FAKE-First");
        action.setSpecialty("FAKE-Specialty");
        action.setAddress1("1 FAKE Street");
        action.setAddress2("Unit FAKE");
        action.setCity("Faketown");
        action.setProvince("BC");
        action.setPostal("V0V0V0");
        action.setPhone("555-0100");
        action.setFax("555-0101");
        return action;
    }

    private Billingreferral storedDoctor(int id, String referralNo) {
        Billingreferral doctor = new Billingreferral();
        doctor.setBillingreferralNo(id);
        doctor.setReferralNo(referralNo);
        doctor.setSpecialty("FAKE-Old");
        return doctor;
    }

    @Test
    @DisplayName("should update every field of the doctor, specialty included, when the form posts its id and keeps its number")
    void shouldUpdateEveryField_whenFormPostsDoctorId() {
        Billingreferral doctor = storedDoctor(7, "90001");
        when(mockBillingreferralDao.getById(7)).thenReturn(doctor);
        when(mockBillingreferralDao.getBillingreferral("90001")).thenReturn(List.of(doctor));
        mockRequest.setParameter("id", "7");

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.SUCCESS);

        // The one stored doctor is updated in place; nothing new is added.
        verify(mockBillingreferralDao).updateBillingreferral(doctor);
        assertThat(doctor.getBillingreferralNo()).isEqualTo(7);
        assertThat(doctor.getLastName()).isEqualTo("FAKE-Last");
        assertThat(doctor.getFirstName()).isEqualTo("FAKE-First");
        assertThat(doctor.getSpecialty()).isEqualTo("FAKE-Specialty");
        assertThat(doctor.getAddress1()).isEqualTo("1 FAKE Street");
        assertThat(doctor.getAddress2()).isEqualTo("Unit FAKE");
        assertThat(doctor.getCity()).isEqualTo("Faketown");
        assertThat(doctor.getFax()).isEqualTo("555-0101");
    }

    @Test
    @DisplayName("should update the doctor to a billing number no one has")
    void shouldUpdateDoctor_whenNewBillingNumberIsFree() {
        Billingreferral doctor = storedDoctor(7, "90009");
        when(mockBillingreferralDao.getById(7)).thenReturn(doctor);
        when(mockBillingreferralDao.getBillingreferral("90001")).thenReturn(null);
        mockRequest.setParameter("id", "7");

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(mockBillingreferralDao).updateBillingreferral(doctor);
        assertThat(doctor.getReferralNo()).isEqualTo("90001");
    }

    @Test
    @DisplayName("should refuse to give the doctor a billing number another doctor already has")
    void shouldRefuseUpdate_whenBillingNumberBelongsToAnotherDoctor() {
        Billingreferral doctor = storedDoctor(7, "90009");
        when(mockBillingreferralDao.getById(7)).thenReturn(doctor);
        when(mockBillingreferralDao.getBillingreferral("90001")).thenReturn(List.of(storedDoctor(8, "90001")));
        mockRequest.setParameter("id", "7");

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.ERROR);

        assertThat(mockRequest.getAttribute("Error")).isEqualTo("Billing # already in use");
        verify(mockBillingreferralDao, never()).updateBillingreferral(any(Billingreferral.class));
        assertThat(doctor.getReferralNo()).isEqualTo("90009");
        assertThat(doctor.getSpecialty()).isEqualTo("FAKE-Old");
    }

    @Test
    @DisplayName("should add a new doctor when the form posts no id and the billing number is free")
    void shouldAddNewDoctor_whenFormPostsNoId() {
        when(mockBillingreferralDao.getBillingreferral("90001")).thenReturn(null);

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<Billingreferral> added = ArgumentCaptor.forClass(Billingreferral.class);
        verify(mockBillingreferralDao).updateBillingreferral(added.capture());
        verify(mockBillingreferralDao, never()).getById(any(Integer.class));
        assertThat(added.getValue().getBillingreferralNo()).isNull();
        assertThat(added.getValue().getReferralNo()).isEqualTo("90001");
        assertThat(added.getValue().getSpecialty()).isEqualTo("FAKE-Specialty");
        assertThat(added.getValue().getCountry()).isEqualTo("Canada");
    }

    @Test
    @DisplayName("should refuse to add a doctor whose billing number is already in use")
    void shouldRefuseAdd_whenBillingNumberInUse() {
        when(mockBillingreferralDao.getBillingreferral("90001")).thenReturn(List.of(new Billingreferral()));

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.ERROR);

        assertThat(mockRequest.getAttribute("Error")).isEqualTo("Billing # already in use");
        verify(mockBillingreferralDao, never()).updateBillingreferral(any(Billingreferral.class));
    }
}
