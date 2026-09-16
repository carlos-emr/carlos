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
import org.mockito.ArgumentCaptor;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.same;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * Security regression coverage for {@link Contact2Action}.
 *
 * @since 2026-05-21
 */
@DisplayName("Contact2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class Contact2ActionUnitTest extends CarlosWebTestBase {

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
            verify(mockDemographicContactDao).merge(same(personal));
            verify(mockDemographicContactDao).merge(same(professional));
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

    private void prepareSave(String personalCount, String professionalCount) {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("contact_num", personalCount);
        addRequestParameter("procontact_num", professionalCount);
    }

    private void prepareFacility() {
        Facility facility = new Facility();
        facility.setId(1);
        when(mockLoggedInInfo.getCurrentFacility()).thenReturn(facility);
    }

    private void addSaveRow(String prefix, String id, String contactId, String type) {
        addRequestParameter(prefix + ".id", id);
        addRequestParameter(prefix + ".contactId", contactId);
        addRequestParameter(prefix + ".type", type);
        addRequestParameter(prefix + ".role", "Parent");
    }

    @Test
    void shouldPersistProfessionalFlags_whenPersonalFieldsHaveOppositeValues() {
        prepareSave("0", "1");
        prepareFacility();
        addSaveRow("procontact_1", "0", "999998", "3");
        addRequestParameter("contact_1.consentToContact", "1");
        addRequestParameter("contact_1.active", "1");
        addRequestParameter("procontact_1.consentToContact", "0");
        addRequestParameter("procontact_1.active", "0");
        withContactDao(() -> {
            assertThat(new Contact2Action().saveManage()).isEqualTo("windowClose");
            ArgumentCaptor<DemographicContact> saved = ArgumentCaptor.forClass(DemographicContact.class);
            verify(mockDemographicContactDao).persist(saved.capture());
            assertThat(saved.getValue().isConsentToContact()).isFalse();
            assertThat(saved.getValue().isActive()).isFalse();
            assertThat(saved.getValue().getCategory()).isEqualTo(DemographicContact.CATEGORY_PROFESSIONAL);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"contact_1", "procontact_1"})
    void shouldRejectForeignSaveBeforeAnyWrite_whenEitherCategoryContainsAnotherPatientsAssociation(String prefix) {
        prepareSave("1", "1");
        addSaveRow("contact_1", "0", "45", "2");
        addSaveRow(prefix, "41", "46", "2");
        DemographicContact foreign = new DemographicContact();
        foreign.setDemographicNo(67890);
        when(mockDemographicContactDao.find(41)).thenReturn(foreign);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::saveManage).isInstanceOf(SecurityException.class);
            assertThat(foreign.getDemographicNo()).isEqualTo(67890);
            verify(mockDemographicContactDao).find(41);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldRejectMissingAssociation_whenSavingAnExistingRow() {
        prepareSave("1", "0");
        addSaveRow("contact_1", "41", "45", "2");
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::saveManage).isInstanceOf(SecurityException.class);
            verify(mockDemographicContactDao).find(41);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldRejectWholeSave_whenRemovalContainsAnotherPatientsAssociation() {
        prepareSave("1", "0");
        addSaveRow("contact_1", "0", "45", "2");
        addRequestParameter("procontact.delete", "41");
        DemographicContact foreign = new DemographicContact();
        foreign.setDemographicNo(67890);
        when(mockDemographicContactDao.find(41)).thenReturn(foreign);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::saveManage).isInstanceOf(SecurityException.class);
            assertThat(foreign.isDeleted()).isFalse();
            verify(mockDemographicContactDao).find(41);
            verifyNoMoreInteractions(mockDemographicContactDao);
        });
    }

    @Test
    void shouldRejectWholeSave_whenReciprocalPatientWriteIsDenied() {
        prepareSave("2", "0");
        addSaveRow("contact_1", "0", "45", "2");
        addSaveRow("contact_2", "0", "67890", "1");
        prepareReverseRole();
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890")))
                .thenReturn(false);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::saveManage).isInstanceOf(SecurityException.class);
            verify(mockDemographicContactDao, never()).persist(any(DemographicContact.class));
            verify(mockDemographicContactDao, never()).merge(any(DemographicContact.class));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCreateSeparateReciprocalRow_whenEditingAnInternalContactWithoutAReverseLink(boolean omitType) {
        prepareSave("1", "0");
        prepareFacility();
        addSaveRow("contact_1", "41", "67890", "1");
        DemographicContact own = new DemographicContact();
        ReflectionTestUtils.setField(own, "id", 41);
        own.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        own.setType(DemographicContact.TYPE_DEMOGRAPHIC);
        if (omitType) mockRequest.removeParameter("contact_1.type");
        when(mockDemographicContactDao.find(41)).thenReturn(own);
        prepareReverseRole();
        withContactDao(() -> {
            new Contact2Action().saveManage();
            assertThat(own.getDemographicNo()).isEqualTo(Integer.parseInt(DEMOGRAPHIC_NO));
            verify(mockDemographicContactDao).merge(same(own));
            ArgumentCaptor<DemographicContact> reverse = ArgumentCaptor.forClass(DemographicContact.class);
            verify(mockDemographicContactDao).persist(reverse.capture());
            assertThat(reverse.getValue().getDemographicNo()).isEqualTo(67890);
            assertThat(reverse.getValue().getContactId()).isEqualTo(DEMOGRAPHIC_NO);
            assertThat(reverse.getValue().getRole()).isEqualTo("Son");
            assertThat(reverse.getValue().getType()).isEqualTo(DemographicContact.TYPE_DEMOGRAPHIC);
            assertThat(reverse.getValue().getSdm()).isEmpty();
            assertThat(reverse.getValue().getEc()).isEmpty();
        });
    }

    private void prepareReverseRole() {
        Demographic patient = new Demographic();
        patient.setSex("M");
        when(mockDemographicDao.getDemographicById(Integer.parseInt(DEMOGRAPHIC_NO))).thenReturn(patient);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectBeforeAnyWrite_whenExistingInternalTypeIsOmittedOrPadded(boolean omitType) {
        prepareSave("2", "0");
        prepareFacility();
        prepareReverseRole();
        addSaveRow("contact_1", "0", "45", "2");
        addSaveRow("contact_2", "41", "67890", "01");
        if (omitType) mockRequest.removeParameter("contact_2.type");
        DemographicContact own = new DemographicContact();
        ReflectionTestUtils.setField(own, "id", 41);
        own.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        own.setType(DemographicContact.TYPE_DEMOGRAPHIC);
        own.setContactId("45678");
        when(mockDemographicContactDao.find(41)).thenReturn(own);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890")))
                .thenReturn(false);
        withContactDao(() -> {
            Contact2Action action = new Contact2Action();
            assertThatThrownBy(action::saveManage).isInstanceOf(SecurityException.class);
            assertThat(own.getContactId()).isEqualTo("45678");
            verify(mockDemographicContactDao, never()).persist(any(DemographicContact.class));
            verify(mockDemographicContactDao, never()).merge(any(DemographicContact.class));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSaveWithoutTargetWriteAccess_whenReverseAssociationAlreadyExists(boolean omitType) {
        prepareSave("1", "1");
        prepareFacility();
        addSaveRow("contact_1", "41", "67890", "1");
        if (omitType) mockRequest.removeParameter("contact_1.type");
        addRequestParameter("contact_1.note", "Updated note");
        addSaveRow("procontact_1", "0", "999998", "3");
        DemographicContact own = new DemographicContact();
        ReflectionTestUtils.setField(own, "id", 41);
        own.setDemographicNo(Integer.parseInt(DEMOGRAPHIC_NO));
        own.setType(DemographicContact.TYPE_DEMOGRAPHIC);
        when(mockDemographicContactDao.find(41)).thenReturn(own);
        when(mockDemographicContactDao.find(67890, Integer.parseInt(DEMOGRAPHIC_NO)))
                .thenReturn(java.util.List.of(new DemographicContact()));
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890")))
                .thenReturn(false);
        withContactDao(() -> {
            assertThat(new Contact2Action().saveManage()).isEqualTo("windowClose");
            assertThat(own.getNote()).isEqualTo("Updated note");
            verify(mockDemographicContactDao).merge(same(own));
            ArgumentCaptor<DemographicContact> saved = ArgumentCaptor.forClass(DemographicContact.class);
            verify(mockDemographicContactDao).persist(saved.capture());
            assertThat(saved.getValue().getCategory()).isEqualTo(DemographicContact.CATEGORY_PROFESSIONAL);
            verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890"));
        });
    }

    @Test
    void shouldSaveWithoutTargetWriteAccess_whenRoleHasNoReverseMapping() {
        prepareSave("1", "0");
        prepareFacility();
        prepareReverseRole();
        addSaveRow("contact_1", "0", "67890", "1");
        mockRequest.setParameter("contact_1.role", "Friend");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890")))
                .thenReturn(false);
        withContactDao(() -> {
            assertThat(new Contact2Action().saveManage()).isEqualTo("windowClose");
            ArgumentCaptor<DemographicContact> saved = ArgumentCaptor.forClass(DemographicContact.class);
            verify(mockDemographicContactDao).persist(saved.capture());
            assertThat(saved.getValue().getDemographicNo()).isEqualTo(Integer.parseInt(DEMOGRAPHIC_NO));
            verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("67890"));
        });
    }

    private void withContactDao(Runnable scenario) {
        // The legacy action caches this bean statically. Isolate each scenario
        // without leaving a different DAO behind for other tests.
        DemographicContactDao previous = Contact2Action.demographicContactDao;
        DemographicDao previousDemographicDao = Contact2Action.demographicDao;
        Contact2Action.demographicDao = mockDemographicDao;
        Contact2Action.demographicContactDao = mockDemographicContactDao;
        try {
            scenario.run();
        } finally {
            Contact2Action.demographicContactDao = previous;
            Contact2Action.demographicDao = previousDemographicDao;
        }
    }
}
