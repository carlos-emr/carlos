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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PharmacyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.struts2.ActionSupport;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Security regression coverage for {@link Contact2Action}.
 *
 * @since 2026-05-21
 */
@DisplayName("Contact2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class Contact2ActionTest extends CarlosWebTestBase {

    private static final String DEMOGRAPHIC_NO = "12345";

    @Mock private ContactDao mockContactDao;
    @Mock private ProfessionalContactDao mockProfessionalContactDao;
    @Mock private DemographicContactDao mockDemographicContactDao;
    @Mock private DemographicDao mockDemographicDao;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private ProviderDao mockProviderDao;
    @Mock private ProfessionalSpecialistDao mockProfessionalSpecialistDao;
    @Mock private ContactSpecialtyDao mockContactSpecialtyDao;
    @Mock private CtlRelationshipsDao mockCtlRelationshipsDao;
    @Mock private PharmacyManager mockPharmacyManager;

    @Test
    @DisplayName("should reject ajax remove when patient record ACL denies write")
    void shouldRejectAjaxRemove_whenPatientRecordAclDeniesWrite() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("postMethod", "ajax");
        when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq(DEMOGRAPHIC_NO)))
                .thenReturn(false);
        Contact2Action action = new Contact2Action();

        assertThatThrownBy(action::removeContact)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_demographic)");

        verify(mockSecurityInfoManager).hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq(DEMOGRAPHIC_NO));
        verifyNoInteractions(mockDemographicContactDao);
    }

    private void registerContactActionBeans() {
        mockRequest.setMethod("POST");
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(ContactDao.class, mockContactDao);
        replaceSpringUtilsBean(ProfessionalContactDao.class, mockProfessionalContactDao);
        replaceSpringUtilsBean(DemographicContactDao.class, mockDemographicContactDao);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        replaceSpringUtilsBean(DemographicManager.class, mockDemographicManager);
        replaceSpringUtilsBean(ProviderDao.class, mockProviderDao);
        replaceSpringUtilsBean(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
        replaceSpringUtilsBean(ContactSpecialtyDao.class, mockContactSpecialtyDao);
        replaceSpringUtilsBean(CtlRelationshipsDao.class, mockCtlRelationshipsDao);
        replaceSpringUtilsBean(PharmacyManager.class, mockPharmacyManager);
    }

    @Test
    void shouldIgnoreUnsavedRows_whenBothContactCategoriesAreRemoved() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("contact.delete", "0");
        addRequestParameter("procontact.delete", "0");
        withContactDao(() -> {
            new Contact2Action().removeContact();
            verifyNoInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldDeletePersistedRows_whenBothContactCategoriesAreSubmitted() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("contact.delete", "41");
        addRequestParameter("procontact.delete", "42");
        DemographicContact personal = new DemographicContact();
        DemographicContact professional = new DemographicContact();
        personal.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        professional.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        when(mockDemographicContactDao.find(41)).thenReturn(personal);
        when(mockDemographicContactDao.find(42)).thenReturn(professional);
        withContactDao(() -> {
            new Contact2Action().removeContact();
            assertThat(personal.isDeleted()).isTrue();
            assertThat(professional.isDeleted()).isTrue();
            verify(mockDemographicContactDao).find(41);
            verify(mockDemographicContactDao).find(42);
            verify(mockDemographicContactDao).merge(personal);
            verify(mockDemographicContactDao).merge(professional);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldPreserveAjaxResponse_whenRemovingAnUnsavedRow() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("postMethod", "ajax");
        addRequestParameter("contactId", "0");
        withContactDao(() -> {
            assertThat(new Contact2Action().removeContact()).isEqualTo("ajax");
            verifyNoInteractions(mockDemographicContactDao);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectUnsupportedMethods_whenRemovingOrSavingContacts(String method) {
        registerContactActionBeans();
        mockRequest.setMethod(method);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThat(action.removeContact()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
            assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
            mockResponse.setStatus(200);
            assertThat(action.saveManage()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(405);
            verifyNoInteractions(mockDemographicContactDao, mockSecurityInfoManager);
        });
    }

    @Test
    void shouldRejectRemoval_whenPatientContextIsMissing() {
        registerContactActionBeans();
        addRequestParameter("contactId", "41");
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::removeContact).isInstanceOf(SecurityException.class);
            verifyNoInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldRejectRemoval_whenAssociationBelongsToAnotherPatient() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("contactId", "41");
        DemographicContact otherPatient = new DemographicContact();
        otherPatient.setDemographicNo(67890);
        when(mockDemographicContactDao.find(41)).thenReturn(otherPatient);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::removeContact).isInstanceOf(SecurityException.class);
            assertThat(otherPatient.isDeleted()).isFalse();
            verify(mockDemographicContactDao).find(41);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldRejectRemoval_whenAssociationDoesNotExist() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("contactId", "41");
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::removeContact).isInstanceOf(SecurityException.class);
            verify(mockDemographicContactDao).find(41);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldLeaveAllRowsUnchanged_whenSelectionContainsAnotherPatientsAssociation() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        // Professional IDs are read first; validate the entire batch before any merge.
        addRequestParameter("procontact.delete", "41");
        addRequestParameter("contact.delete", "42");
        DemographicContact ownPatient = new DemographicContact();
        ownPatient.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        DemographicContact otherPatient = new DemographicContact();
        otherPatient.setDemographicNo(67890);
        when(mockDemographicContactDao.find(41)).thenReturn(ownPatient);
        when(mockDemographicContactDao.find(42)).thenReturn(otherPatient);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::removeContact).isInstanceOf(SecurityException.class);
            assertThat(ownPatient.isDeleted()).isFalse();
            assertThat(otherPatient.isDeleted()).isFalse();
            verify(mockDemographicContactDao).find(41);
            verify(mockDemographicContactDao).find(42);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    private void withContactDao(Runnable scenario) {
        // The legacy action caches this bean statically. Isolate each scenario
        // without leaving a different DAO behind for other tests.
        DemographicContactDao previous = Contact2Action.demographicContactDao;
        Contact2Action.demographicContactDao = mockDemographicContactDao;
        try {
            scenario.run();
        } finally {
            Contact2Action.demographicContactDao = previous;
        }
    }
}
