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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that every Manager DTO method added in the DTO projection layer
 * throws on privilege denial rather than silently returning an empty list.
 *
 * <p>A copy-paste regression (wrong sec-object string, missing privilege check,
 * or swallowed denial) would silently leak PHI. These tests lock in the contract
 * that denial surfaces as an exception and that the backing DAO is not consulted.</p>
 *
 * @since 2026-04-12
 */
@DisplayName("DTO Manager Security — privilege-denial contract")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("security")
public class DtoManagerSecurityUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMO_NO = 12345;
    private static final String DEMO_NO_STR = "12345";
    private static final String PROVIDER_NO = "999990";

    private SecurityInfoManager mockSecurityInfoManager;
    private LoggedInInfo mockLoggedInInfo;

    @BeforeEach
    void setUpSecurityMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        denyAllPrivileges();
    }

    private void denyAllPrivileges() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("AllergyManagerImpl.getAllergyDTOs should throw SecurityException and skip DAO when _allergy denied")
    void allergyManager_shouldThrow_whenAllergyReadDenied() {
        AllergyDao dao = Mockito.mock(AllergyDao.class);
        AllergyManagerImpl manager = new AllergyManagerImpl();
        injectDependency(manager, "allergyDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getAllergyDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");
        verify(dao, never()).findAllergyDTOsByDemographicNo(any());
    }

    @Test
    @DisplayName("AppointmentManagerImpl.getDayAppointmentDTOs should throw SecurityException and skip DAO when _appointment denied")
    void appointmentManager_shouldThrow_whenAppointmentReadDenied() {
        OscarAppointmentDao dao = Mockito.mock(OscarAppointmentDao.class);
        AppointmentManagerImpl manager = new AppointmentManagerImpl();
        injectDependency(manager, "appointmentDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getDayAppointmentDTOs(mockLoggedInInfo, new Date(), PROVIDER_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment");
        verify(dao, never()).findDayAppointmentDTOs(any(), anyString());
    }

    @Test
    @DisplayName("ConsultationManagerImpl.getConsultationDTOs should throw SecurityException and skip DAO when _con denied")
    void consultationManager_shouldThrow_whenConReadDenied() {
        ConsultationRequestDao dao = Mockito.mock(ConsultationRequestDao.class);
        ConsultationManagerImpl manager = new ConsultationManagerImpl();
        injectDependency(manager, "consultationRequestDtoDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getConsultationDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_con");
        verify(dao, never()).findConsultationDTOsByDemographicId(any());
    }

    @Test
    @DisplayName("DocumentManagerImpl.getDocumentDTOs should throw SecurityException and skip DAO when _edoc denied")
    void documentManager_shouldThrow_whenEdocReadDenied() {
        DocumentDao dao = Mockito.mock(DocumentDao.class);
        DocumentManagerImpl manager = new DocumentManagerImpl();
        injectDependency(manager, "documentDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getDocumentDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");
        verify(dao, never()).findDocumentDTOsByDemographicNo(any());
    }

    @Test
    @DisplayName("PrescriptionManagerImpl.getDrugDTOs should throw AccessDeniedException (scoped) and skip DAO when _demographic denied")
    void prescriptionManager_shouldThrow_whenDemographicReadDenied() {
        DrugDao dao = Mockito.mock(DrugDao.class);
        PrescriptionManagerImpl manager = new PrescriptionManagerImpl();
        injectDependency(manager, "drugDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getDrugDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(AccessDeniedException.class);
        verify(dao, never()).findDrugDTOsByDemographicId(any());
    }

    @Test
    @DisplayName("PreventionManagerImpl.getPreventionDTOs should throw SecurityException and skip DAO when _prevention denied")
    void preventionManager_shouldThrow_whenPreventionReadDenied() {
        PreventionDao dao = Mockito.mock(PreventionDao.class);
        PreventionManagerImpl manager = new PreventionManagerImpl();
        injectDependency(manager, "preventionDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getPreventionDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_prevention");
        verify(dao, never()).findPreventionDTOsByDemographicId(any());
    }

    @Test
    @DisplayName("BillingONManager.getBillingDTOs should throw SecurityException and skip DAO when _billing denied")
    void billingONManager_shouldThrow_whenBillingReadDenied() {
        BillingONCHeader1Dao dao = Mockito.mock(BillingONCHeader1Dao.class);
        BillingONManager manager = new BillingONManager();
        injectDependency(manager, "billingONCHeader1Dao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getBillingDTOs(mockLoggedInInfo, DEMO_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
        verify(dao, never()).findBillingDTOsByDemographicNo(any());
    }

    @Test
    @DisplayName("CaseManagementManagerImpl.getIssueDTOs should throw SecurityException and skip DAO when _demographic denied")
    void caseManagementManager_getIssueDTOs_shouldThrow_whenDemographicReadDenied() {
        CaseManagementIssueDAO issueDao = Mockito.mock(CaseManagementIssueDAO.class);
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementIssueDAO", issueDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getIssueDTOs(mockLoggedInInfo, DEMO_NO_STR))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_demographic");
        verify(issueDao, never()).findIssueDTOsByDemographicNo(anyString());
    }

    @Test
    @DisplayName("CaseManagementManagerImpl.getNoteDTOs should throw SecurityException and skip DAO when _demographic denied")
    void caseManagementManager_getNoteDTOs_shouldThrow_whenDemographicReadDenied() {
        CaseManagementNoteDAO noteDao = Mockito.mock(CaseManagementNoteDAO.class);
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementNoteDAO", noteDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        assertThatThrownBy(() -> manager.getNoteDTOs(mockLoggedInInfo, DEMO_NO_STR))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_demographic");
        verify(noteDao, never()).findNoteDTOsByDemographicNo(anyString());
    }
}
