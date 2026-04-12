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

import io.github.carlos_emr.carlos.allergy.dto.AllergyListItemDTO;
import io.github.carlos_emr.carlos.appointment.dto.AppointmentListItemDTO;
import io.github.carlos_emr.carlos.billings.dto.BillingONCListItemDTO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementIssueListDTO;
import io.github.carlos_emr.carlos.casemgmt.dto.CaseManagementNoteListDTO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationRequestListItemDTO;
import io.github.carlos_emr.carlos.documentManager.dto.DocumentListItemDTO;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.prescript.dto.DrugListItemDTO;
import io.github.carlos_emr.carlos.prevention.dto.PreventionListItemDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that every Manager DTO method added in the DTO projection layer
 * behaves correctly on both the denial and the allow path.
 *
 * <p>Denial tests lock in that a missing privilege surfaces as an exception and
 * that the backing DAO is NOT consulted — preventing a silent PHI leak if a
 * sec-object string or privilege mode regresses.</p>
 *
 * <p>Allow tests lock in the opposite half of the contract: granted privilege
 * must reach the DAO, return the DAO's result unchanged, and record a
 * synchronous audit log entry. A typo on the allow branch (wrong sec-object
 * string that the user happens to have) would pass all denial tests but be
 * caught here.</p>
 *
 * @since 2026-04-12
 */
@DisplayName("DTO Manager Security — privilege contract (denial + allow)")
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
        // SecurityInfoManager has both int and String overloads for the scoped arg.
        // Stub both so primitive-int call sites (Prescription/Prevention) hit a stub
        // rather than Mockito's default-false (which would pass by coincidence).
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyInt()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull()))
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

    // ---------------------------------------------------------------------
    // Allow-path tests — privilege granted ⇒ DAO called, result returned,
    // audit entry written. These catch regressions where a sec-object string
    // typo makes the check pass against the wrong object, or where audit
    // logging is accidentally removed.
    // ---------------------------------------------------------------------

    private void grantPrivilege(String secObject) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(secObject), anyString(), anyString()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(secObject), anyString(), anyInt()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(secObject), anyString(), isNull()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("AllergyManagerImpl.getAllergyDTOs should call DAO and log when _allergy granted")
    void allergyManager_shouldCallDaoAndLog_whenAllergyReadGranted() {
        AllergyDao dao = Mockito.mock(AllergyDao.class);
        List<AllergyListItemDTO> expected = Collections.singletonList(new AllergyListItemDTO());
        when(dao.findAllergyDTOsByDemographicNo(DEMO_NO)).thenReturn(expected);
        AllergyManagerImpl manager = new AllergyManagerImpl();
        injectDependency(manager, "allergyDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_allergy");

        List<AllergyListItemDTO> result = manager.getAllergyDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findAllergyDTOsByDemographicNo(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("AllergyManager.getAllergyDTOs"), anyString()));
    }

    @Test
    @DisplayName("AppointmentManagerImpl.getDayAppointmentDTOs should call DAO and log when _appointment granted")
    void appointmentManager_shouldCallDaoAndLog_whenAppointmentReadGranted() {
        OscarAppointmentDao dao = Mockito.mock(OscarAppointmentDao.class);
        Date date = new Date();
        List<AppointmentListItemDTO> expected = Collections.singletonList(new AppointmentListItemDTO());
        when(dao.findDayAppointmentDTOs(date, PROVIDER_NO)).thenReturn(expected);
        AppointmentManagerImpl manager = new AppointmentManagerImpl();
        injectDependency(manager, "appointmentDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_appointment");

        List<AppointmentListItemDTO> result = manager.getDayAppointmentDTOs(mockLoggedInInfo, date, PROVIDER_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findDayAppointmentDTOs(date, PROVIDER_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("AppointmentManager.getDayAppointmentDTOs"), anyString()));
    }

    @Test
    @DisplayName("ConsultationManagerImpl.getConsultationDTOs should call DAO and log when _con granted")
    void consultationManager_shouldCallDaoAndLog_whenConReadGranted() {
        ConsultationRequestDao dao = Mockito.mock(ConsultationRequestDao.class);
        List<ConsultationRequestListItemDTO> expected = Collections.singletonList(new ConsultationRequestListItemDTO());
        when(dao.findConsultationDTOsByDemographicId(DEMO_NO)).thenReturn(expected);
        ConsultationManagerImpl manager = new ConsultationManagerImpl();
        injectDependency(manager, "consultationRequestDtoDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_con");

        List<ConsultationRequestListItemDTO> result = manager.getConsultationDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findConsultationDTOsByDemographicId(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("ConsultationManager.getConsultationDTOs"), anyString()));
    }

    @Test
    @DisplayName("DocumentManagerImpl.getDocumentDTOs should call DAO and log when _edoc granted")
    void documentManager_shouldCallDaoAndLog_whenEdocReadGranted() {
        DocumentDao dao = Mockito.mock(DocumentDao.class);
        List<DocumentListItemDTO> expected = Collections.singletonList(new DocumentListItemDTO());
        when(dao.findDocumentDTOsByDemographicNo(DEMO_NO)).thenReturn(expected);
        DocumentManagerImpl manager = new DocumentManagerImpl();
        injectDependency(manager, "documentDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_edoc");

        List<DocumentListItemDTO> result = manager.getDocumentDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findDocumentDTOsByDemographicNo(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("DocumentManager.getDocumentDTOs"), anyString()));
    }

    @Test
    @DisplayName("PrescriptionManagerImpl.getDrugDTOs should call DAO and log when _demographic granted")
    void prescriptionManager_shouldCallDaoAndLog_whenDemographicReadGranted() {
        DrugDao dao = Mockito.mock(DrugDao.class);
        List<DrugListItemDTO> expected = Collections.singletonList(new DrugListItemDTO());
        when(dao.findDrugDTOsByDemographicId(DEMO_NO)).thenReturn(expected);
        PrescriptionManagerImpl manager = new PrescriptionManagerImpl();
        injectDependency(manager, "drugDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_demographic");

        List<DrugListItemDTO> result = manager.getDrugDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findDrugDTOsByDemographicId(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("PrescriptionManager.getDrugDTOs"), anyString()));
    }

    @Test
    @DisplayName("PreventionManagerImpl.getPreventionDTOs should call DAO and log when _prevention granted")
    void preventionManager_shouldCallDaoAndLog_whenPreventionReadGranted() {
        PreventionDao dao = Mockito.mock(PreventionDao.class);
        List<PreventionListItemDTO> expected = Collections.singletonList(new PreventionListItemDTO());
        when(dao.findPreventionDTOsByDemographicId(DEMO_NO)).thenReturn(expected);
        PreventionManagerImpl manager = new PreventionManagerImpl();
        injectDependency(manager, "preventionDao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_prevention");

        List<PreventionListItemDTO> result = manager.getPreventionDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findPreventionDTOsByDemographicId(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("PreventionManager.getPreventionDTOs"), anyString()));
    }

    @Test
    @DisplayName("BillingONManager.getBillingDTOs should call DAO and log when _billing granted")
    void billingONManager_shouldCallDaoAndLog_whenBillingReadGranted() {
        BillingONCHeader1Dao dao = Mockito.mock(BillingONCHeader1Dao.class);
        List<BillingONCListItemDTO> expected = Collections.singletonList(new BillingONCListItemDTO());
        when(dao.findBillingDTOsByDemographicNo(DEMO_NO)).thenReturn(expected);
        BillingONManager manager = new BillingONManager();
        injectDependency(manager, "billingONCHeader1Dao", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_billing");

        List<BillingONCListItemDTO> result = manager.getBillingDTOs(mockLoggedInInfo, DEMO_NO);

        assertThat(result).isSameAs(expected);
        verify(dao).findBillingDTOsByDemographicNo(DEMO_NO);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("BillingONManager.getBillingDTOs"), anyString()));
    }

    @Test
    @DisplayName("CaseManagementManagerImpl.getIssueDTOs should call DAO and log when _demographic granted")
    void caseManagementManager_getIssueDTOs_shouldCallDaoAndLog_whenDemographicReadGranted() {
        CaseManagementIssueDAO dao = Mockito.mock(CaseManagementIssueDAO.class);
        List<CaseManagementIssueListDTO> expected = Collections.singletonList(new CaseManagementIssueListDTO());
        when(dao.findIssueDTOsByDemographicNo(DEMO_NO_STR)).thenReturn(expected);
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementIssueDAO", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_demographic");

        List<CaseManagementIssueListDTO> result = manager.getIssueDTOs(mockLoggedInInfo, DEMO_NO_STR);

        assertThat(result).isSameAs(expected);
        verify(dao).findIssueDTOsByDemographicNo(DEMO_NO_STR);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("CaseManagementManager.getIssueDTOs"), anyString()));
    }

    @Test
    @DisplayName("CaseManagementManagerImpl.getNoteDTOs should call DAO and log when _demographic granted")
    void caseManagementManager_getNoteDTOs_shouldCallDaoAndLog_whenDemographicReadGranted() {
        CaseManagementNoteDAO dao = Mockito.mock(CaseManagementNoteDAO.class);
        List<CaseManagementNoteListDTO> expected = Collections.singletonList(new CaseManagementNoteListDTO());
        when(dao.findNoteDTOsByDemographicNo(DEMO_NO_STR)).thenReturn(expected);
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementNoteDAO", dao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        grantPrivilege("_demographic");

        List<CaseManagementNoteListDTO> result = manager.getNoteDTOs(mockLoggedInInfo, DEMO_NO_STR);

        assertThat(result).isSameAs(expected);
        verify(dao).findNoteDTOsByDemographicNo(DEMO_NO_STR);
        logActionMock.verify(() -> LogAction.addLogSynchronous(
                eq(mockLoggedInInfo), eq("CaseManagementManager.getNoteDTOs"), anyString()));
    }
}
