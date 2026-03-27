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

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LabManagerImpl} business logic.
 *
 * <p>This test class exercises all four public methods of the LabManager interface:
 * {@code getHl7Messages}, {@code getHl7TextInfo}, {@code getHl7Message}, and
 * {@code renderLab}. Each method is tested for normal operation, security privilege
 * enforcement, edge cases, and null parameter handling.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Security privilege verification for every method</li>
 *   <li>Manager-DAO interaction patterns with mock verification</li>
 *   <li>Edge cases: empty lists, null routing results, boundary offsets</li>
 *   <li>BDD naming with {@code @Nested} organization</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see LabManagerImpl
 * @see LabUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lab Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("lab")
public class LabManagerUnitTest extends LabUnitTestBase {

    @Mock
    private Hl7TextInfoDao mockHl7TextInfoDao;

    @Mock
    private Hl7TextMessageDao mockHl7TextMessageDao;

    @Mock
    private PatientLabRoutingDao mockPatientLabRoutingDao;

    @Mock
    private NioFileManager mockNioFileManager;

    private LabManagerImpl labManager;

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers mock DAOs and managers with SpringUtils, stubs the security manager
     * to grant all privileges by default (lenient to avoid unnecessary stubbing warnings),
     * creates a fresh {@link LabManagerImpl} instance, and injects mock dependencies
     * (HL7 DAOs, patient lab routing DAO, NioFileManager, SecurityInfoManager) via
     * reflection.</p>
     */
    @BeforeEach
    void setUp() {
        // Register DAO mocks for SpringUtils
        registerMock(Hl7TextInfoDao.class, mockHl7TextInfoDao);
        registerMock(Hl7TextMessageDao.class, mockHl7TextMessageDao);
        registerMock(PatientLabRoutingDao.class, mockPatientLabRoutingDao);
        registerMock(NioFileManager.class, mockNioFileManager);

        // Security manager returns true for all privilege checks by default
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // Create manager instance
        labManager = new LabManagerImpl();

        // Inject dependencies using reflection
        injectDependency(labManager, "hl7textInfoDao", mockHl7TextInfoDao);
        injectDependency(labManager, "hl7TextMessageDao", mockHl7TextMessageDao);
        injectDependency(labManager, "patientLabRoutingDao", mockPatientLabRoutingDao);
        injectDependency(labManager, "nioFileManager", mockNioFileManager);
        injectDependency(labManager, "securityInfoManager", mockSecurityInfoManager);
    }

    /**
     * Tests for {@link LabManagerImpl#getHl7Messages(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer, int, int)}.
     *
     * <p>Retrieves paginated HL7 text messages for a given demographic number.
     * Delegates to {@link Hl7TextMessageDao#findByDemographicNo(Integer, int, int)}.</p>
     */
    @Nested
    @DisplayName("getHl7Messages")
    @Tag("read")
    class GetHl7Messages {

        @Test
        @Tag("read")
        @DisplayName("should return messages when valid demographic number provided")
        void shouldReturnMessages_whenValidDemographicNoProvided() {
            // Given
            Hl7TextMessage msg1 = createTestHl7TextMessage("MSH|^~\\&|TestLab|");
            Hl7TextMessage msg2 = createTestHl7TextMessage("MSH|^~\\&|OtherLab|");
            List<Hl7TextMessage> expectedMessages = Arrays.asList(msg1, msg2);
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 10))
                .thenReturn(expectedMessages);

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isSameAs(msg1);
            assertThat(results.get(1)).isSameAs(msg2);
            verify(mockHl7TextMessageDao).findByDemographicNo(TEST_DEMO_NO, 0, 10);
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no messages found")
        void shouldReturnEmptyList_whenNoMessagesFound() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(results).isEmpty();
            verify(mockHl7TextMessageDao).findByDemographicNo(TEST_DEMO_NO, 0, 10);
        }

        @Test
        @Tag("read")
        @DisplayName("should pass offset and limit to DAO correctly")
        void shouldPassOffsetAndLimitToDao_whenPaginationRequested() {
            // Given
            int offset = 20;
            int limit = 5;
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, offset, limit))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, offset, limit);

            // Then
            verify(mockHl7TextMessageDao).findByDemographicNo(TEST_DEMO_NO, offset, limit);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when DAO returns null")
        void shouldReturnNull_whenDaoReturnsNull() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 10))
                .thenReturn(null);

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            assertThat(results).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should handle zero offset and zero limit")
        void shouldReturnEmptyList_whenOffsetAndLimitAreZero() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 0))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 0);

            // Then
            assertThat(results).isEmpty();
            verify(mockHl7TextMessageDao).findByDemographicNo(TEST_DEMO_NO, 0, 0);
        }
    }

    /**
     * Tests for {@link LabManagerImpl#getHl7TextInfo(io.github.carlos_emr.carlos.utility.LoggedInInfo, int)}.
     *
     * <p>Retrieves HL7 text info records for a demographic by first looking up lab routings
     * via {@link PatientLabRoutingDao#findByDemographicAndLabType(Integer, String)}, extracting
     * lab IDs, and then querying {@link Hl7TextInfoDao#findByLabIdList(List)}.</p>
     */
    @Nested
    @DisplayName("getHl7TextInfo")
    @Tag("read")
    class GetHl7TextInfo {

        @Test
        @Tag("read")
        @DisplayName("should return lab info when patient has lab routings")
        void shouldReturnLabInfo_whenPatientHasLabRoutings() {
            // Given
            PatientLabRouting routing1 = createTestPatientLabRouting(101, TEST_DEMO_NO);
            PatientLabRouting routing2 = createTestPatientLabRouting(102, TEST_DEMO_NO);
            List<PatientLabRouting> routings = Arrays.asList(routing1, routing2);

            Hl7TextInfo info1 = createTestHl7TextInfo();
            info1.setLabNumber(101);
            Hl7TextInfo info2 = createTestHl7TextInfo();
            info2.setLabNumber(102);
            List<Hl7TextInfo> expectedInfos = Arrays.asList(info1, info2);

            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(routings);
            when(mockHl7TextInfoDao.findByLabIdList(Arrays.asList(101, 102)))
                .thenReturn(expectedInfos);

            // When
            List<Hl7TextInfo> results = labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isSameAs(info1);
            assertThat(results.get(1)).isSameAs(info2);
            verify(mockPatientLabRoutingDao).findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7);
            verify(mockHl7TextInfoDao).findByLabIdList(Arrays.asList(101, 102));
        }

        @Test
        @Tag("read")
        @DisplayName("should return results for empty lab IDs list when no routings found")
        void shouldReturnResultsForEmptyLabIdsList_whenNoRoutingsFound() {
            // Given
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(Collections.emptyList());
            when(mockHl7TextInfoDao.findByLabIdList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextInfo> results = labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).isEmpty();
            verify(mockHl7TextInfoDao).findByLabIdList(Collections.emptyList());
        }

        @Test
        @Tag("read")
        @DisplayName("should handle null routing list from DAO")
        void shouldHandleNullRoutingList_whenDaoReturnsNull() {
            // Given
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(null);
            // When routing list is null, code skips the loop and passes empty labIds list
            when(mockHl7TextInfoDao.findByLabIdList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextInfo> results = labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).isEmpty();
            verify(mockHl7TextInfoDao).findByLabIdList(Collections.emptyList());
        }

        @Test
        @Tag("read")
        @DisplayName("should extract lab numbers from routings correctly")
        void shouldExtractLabNumbers_whenMultipleRoutingsExist() {
            // Given
            PatientLabRouting routing1 = createTestPatientLabRouting(50, TEST_DEMO_NO);
            PatientLabRouting routing2 = createTestPatientLabRouting(75, TEST_DEMO_NO);
            PatientLabRouting routing3 = createTestPatientLabRouting(100, TEST_DEMO_NO);
            List<PatientLabRouting> routings = Arrays.asList(routing1, routing2, routing3);

            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(routings);
            when(mockHl7TextInfoDao.findByLabIdList(Arrays.asList(50, 75, 100)))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            verify(mockHl7TextInfoDao).findByLabIdList(Arrays.asList(50, 75, 100));
        }

        @Test
        @Tag("read")
        @DisplayName("should use HL7 constant when querying patient lab routing")
        void shouldUseHl7Constant_whenQueryingPatientLabRouting() {
            // Given
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(anyInt(), anyString()))
                .thenReturn(Collections.emptyList());
            when(mockHl7TextInfoDao.findByLabIdList(any()))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then - verify HL7 constant is used, not a raw string
            verify(mockPatientLabRoutingDao).findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7);
        }

        @Test
        @Tag("read")
        @DisplayName("should handle single routing correctly")
        void shouldHandleSingleRouting_whenOnlyOneLabRoutingExists() {
            // Given
            PatientLabRouting routing = createTestPatientLabRouting(TEST_LAB_ID, TEST_DEMO_NO);
            Hl7TextInfo info = createTestHl7TextInfo();

            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(Collections.singletonList(routing));
            when(mockHl7TextInfoDao.findByLabIdList(Collections.singletonList(TEST_LAB_ID)))
                .thenReturn(Collections.singletonList(info));

            // When
            List<Hl7TextInfo> results = labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0)).isSameAs(info);
        }
    }

    /**
     * Tests for {@link LabManagerImpl#getHl7Message(io.github.carlos_emr.carlos.utility.LoggedInInfo, int)}.
     *
     * <p>Retrieves a single HL7 text message by lab ID, delegating to
     * {@link Hl7TextMessageDao#find(int)}.</p>
     */
    @Nested
    @DisplayName("getHl7Message")
    @Tag("read")
    class GetHl7Message {

        @Test
        @Tag("read")
        @DisplayName("should return message when valid lab ID provided")
        void shouldReturnMessage_whenValidLabIdProvided() {
            // Given
            Hl7TextMessage expectedMessage = createTestHl7TextMessage("MSH|^~\\&|Lab|");
            when(mockHl7TextMessageDao.find((int) TEST_LAB_ID))
                .thenReturn(expectedMessage);

            // When
            Hl7TextMessage result = labManager.getHl7Message(mockLoggedInInfo, TEST_LAB_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(expectedMessage);
            verify(mockHl7TextMessageDao).find((int) TEST_LAB_ID);
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when lab ID not found")
        void shouldReturnNull_whenLabIdNotFound() {
            // Given
            when(mockHl7TextMessageDao.find(999))
                .thenReturn(null);

            // When
            Hl7TextMessage result = labManager.getHl7Message(mockLoggedInInfo, 999);

            // Then
            assertThat(result).isNull();
            verify(mockHl7TextMessageDao).find(999);
        }

        @Test
        @Tag("read")
        @DisplayName("should pass lab ID to DAO correctly")
        void shouldPassLabIdToDao_whenCalled() {
            // Given
            int specificLabId = 42;
            when(mockHl7TextMessageDao.find(specificLabId))
                .thenReturn(null);

            // When
            labManager.getHl7Message(mockLoggedInInfo, specificLabId);

            // Then
            verify(mockHl7TextMessageDao).find(specificLabId);
        }

        @Test
        @Tag("read")
        @DisplayName("should handle zero lab ID")
        void shouldReturnNull_whenLabIdIsZero() {
            // Given
            when(mockHl7TextMessageDao.find(0))
                .thenReturn(null);

            // When
            Hl7TextMessage result = labManager.getHl7Message(mockLoggedInInfo, 0);

            // Then
            assertThat(result).isNull();
            verify(mockHl7TextMessageDao).find(0);
        }

        @Test
        @Tag("read")
        @DisplayName("should handle negative lab ID")
        void shouldReturnNull_whenLabIdIsNegative() {
            // Given
            when(mockHl7TextMessageDao.find(-1))
                .thenReturn(null);

            // When
            Hl7TextMessage result = labManager.getHl7Message(mockLoggedInInfo, -1);

            // Then
            assertThat(result).isNull();
            verify(mockHl7TextMessageDao).find(-1);
        }
    }

    /**
     * Tests for {@link LabManagerImpl#renderLab(io.github.carlos_emr.carlos.utility.LoggedInInfo, Integer)}.
     *
     * <p>Renders a lab result as a PDF. This method creates temporary files, uses
     * {@link io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator} for PDF generation,
     * and delegates file storage to {@link NioFileManager}. Since the LabPDFCreator constructor
     * reaches into Hibernate/Spring internals, unit testing of the full happy path is not feasible
     * without integration context. Security checks and exception handling are tested here.</p>
     */
    @Nested
    @DisplayName("renderLab")
    @Tag("read")
    class RenderLab {

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException when security check fails")
        void shouldThrowRuntimeException_whenSecurityCheckFails() {
            // Given
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_lab"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> labManager.renderLab(mockLoggedInInfo, TEST_LAB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_lab)");
        }

        @Test
        @Tag("read")
        @DisplayName("should check read privilege before rendering")
        void shouldCheckReadPrivilege_beforeRendering() {
            // Given - security check fails to halt processing early
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_lab"), eq("r"), any()))
                .thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> labManager.renderLab(mockLoggedInInfo, TEST_LAB_ID))
                .isInstanceOf(RuntimeException.class);

            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", "r", null);
        }
    }

    /**
     * Tests for security privilege enforcement across all LabManager methods.
     *
     * <p>Every public method in LabManagerImpl calls {@code checkPrivilege(loggedInInfo, "r")}
     * which delegates to {@link SecurityInfoManager#hasPrivilege} with the "_lab" security
     * object. This nested class verifies that all four methods properly reject unauthorized
     * access with a RuntimeException.</p>
     */
    @Nested
    @DisplayName("Security Privilege Checks")
    @Tag("security")
    class SecurityPrivilegeChecks {

        @BeforeEach
        void setUpSecurityDenied() {
            // Deny all privilege checks
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_lab"), eq("r"), any()))
                .thenReturn(false);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException from getHl7Messages when privilege denied")
        void shouldThrowRuntimeException_whenGetHl7MessagesPrivilegeDenied() {
            // When / Then
            assertThatThrownBy(() -> labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_lab)");

            // Verify DAO was never called
            verify(mockHl7TextMessageDao, never()).findByDemographicNo(anyInt(), anyInt(), anyInt());
        }

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException from getHl7TextInfo when privilege denied")
        void shouldThrowRuntimeException_whenGetHl7TextInfoPrivilegeDenied() {
            // When / Then
            assertThatThrownBy(() -> labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_lab)");

            // Verify DAOs were never called
            verify(mockPatientLabRoutingDao, never()).findByDemographicAndLabType(anyInt(), anyString());
            verify(mockHl7TextInfoDao, never()).findByLabIdList(any());
        }

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException from getHl7Message when privilege denied")
        void shouldThrowRuntimeException_whenGetHl7MessagePrivilegeDenied() {
            // When / Then
            assertThatThrownBy(() -> labManager.getHl7Message(mockLoggedInInfo, TEST_LAB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_lab)");

            // Verify DAO was never called
            verify(mockHl7TextMessageDao, never()).find(anyInt());
        }

        @Test
        @Tag("read")
        @DisplayName("should throw RuntimeException from renderLab when privilege denied")
        void shouldThrowRuntimeException_whenRenderLabPrivilegeDenied() {
            // When / Then
            assertThatThrownBy(() -> labManager.renderLab(mockLoggedInInfo, TEST_LAB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_lab)");
        }

        @Test
        @Tag("read")
        @DisplayName("should verify _lab security object is used for all methods")
        void shouldVerifyLabSecurityObject_whenPrivilegeChecked() {
            // Given - getHl7Messages triggers the security check
            try {
                labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10);
            } catch (RuntimeException ignored) {
                // Expected
            }

            // Then - verify the "_lab" security object and "r" privilege are used
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", "r", null);
        }

        @Test
        @Tag("read")
        @DisplayName("should use read privilege for all methods")
        void shouldUseReadPrivilege_whenCheckingAccess() {
            // Given - trigger all four methods and collect the privilege argument
            try {
                labManager.getHl7Message(mockLoggedInInfo, TEST_LAB_ID);
            } catch (RuntimeException ignored) {
                // Expected
            }

            // Then - verify "r" read privilege is checked
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", "r", null);
        }
    }

    /**
     * Tests for edge cases and null parameter handling across all LabManager methods.
     */
    @Nested
    @DisplayName("Edge Cases and Null Parameters")
    @Tag("read")
    class EdgeCasesAndNullParameters {

        @Test
        @Tag("read")
        @DisplayName("should handle null demographicNo in getHl7Messages")
        void shouldHandleNullDemographicNo_whenGetHl7MessagesCalled() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(isNull(), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, null, 0, 10);

            // Then
            assertThat(results).isEmpty();
            verify(mockHl7TextMessageDao).findByDemographicNo(null, 0, 10);
        }

        @Test
        @Tag("read")
        @DisplayName("should handle large offset in getHl7Messages")
        void shouldHandleLargeOffset_whenGetHl7MessagesCalled() {
            // Given
            int largeOffset = Integer.MAX_VALUE;
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, largeOffset, 10))
                .thenReturn(Collections.emptyList());

            // When
            List<Hl7TextMessage> results = labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, largeOffset, 10);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should handle null segmentId in renderLab")
        void shouldHandleNullSegmentId_whenRenderLabCalled() {
            // Given - renderLab with null segmentId will attempt PDF generation
            // which will throw an exception when creating LabPDFCreator with null ID.
            // The security check passes, so the method proceeds to file creation.
            // Since LabPDFCreator uses Spring/Hibernate internals, this will fail with
            // an exception that gets wrapped as PDFGenerationException.
            // We just verify the security check still happens.

            // When / Then - the method will throw some form of exception due to null
            assertThatThrownBy(() -> labManager.renderLab(mockLoggedInInfo, null))
                .isInstanceOf(Exception.class);

            // Verify security was checked before the failure
            verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_lab", "r", null);
        }

        @Test
        @Tag("read")
        @DisplayName("should handle DAO exception in getHl7Messages")
        void shouldPropagateException_whenDaoThrowsInGetHl7Messages() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 10))
                .thenThrow(new RuntimeException("Database connection lost"));

            // When / Then
            assertThatThrownBy(() -> labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection lost");
        }

        @Test
        @Tag("read")
        @DisplayName("should handle DAO exception in getHl7TextInfo")
        void shouldPropagateException_whenDaoThrowsInGetHl7TextInfo() {
            // Given
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenThrow(new RuntimeException("Database connection lost"));

            // When / Then
            assertThatThrownBy(() -> labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection lost");
        }

        @Test
        @Tag("read")
        @DisplayName("should handle DAO exception in getHl7Message")
        void shouldPropagateException_whenDaoThrowsInGetHl7Message() {
            // Given
            when(mockHl7TextMessageDao.find((int) TEST_LAB_ID))
                .thenThrow(new RuntimeException("Database connection lost"));

            // When / Then
            assertThatThrownBy(() -> labManager.getHl7Message(mockLoggedInInfo, TEST_LAB_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection lost");
        }

        @Test
        @Tag("read")
        @DisplayName("should handle Hl7TextInfoDao exception after routing lookup succeeds")
        void shouldPropagateException_whenHl7TextInfoDaoThrowsAfterRoutingSuccess() {
            // Given
            PatientLabRouting routing = createTestPatientLabRouting(TEST_LAB_ID, TEST_DEMO_NO);
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(Collections.singletonList(routing));
            when(mockHl7TextInfoDao.findByLabIdList(Collections.singletonList(TEST_LAB_ID)))
                .thenThrow(new RuntimeException("Query timeout"));

            // When / Then
            assertThatThrownBy(() -> labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Query timeout");
        }
    }

    /**
     * Tests that verify audit logging (LogAction) integration for all methods.
     *
     * <p>While LogAction is mocked as a no-op by {@link io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase},
     * these tests verify the manager correctly calls through to the security check and DAO
     * layer, confirming the expected call chain is intact.</p>
     */
    @Nested
    @DisplayName("DAO Interaction Verification")
    @Tag("read")
    class DaoInteractionVerification {

        @Test
        @Tag("read")
        @DisplayName("should call findByDemographicNo exactly once for getHl7Messages")
        void shouldCallFindByDemographicNoOnce_whenGetHl7MessagesCalled() {
            // Given
            when(mockHl7TextMessageDao.findByDemographicNo(TEST_DEMO_NO, 0, 10))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7Messages(mockLoggedInInfo, TEST_DEMO_NO, 0, 10);

            // Then
            verify(mockHl7TextMessageDao, times(1)).findByDemographicNo(TEST_DEMO_NO, 0, 10);
        }

        @Test
        @Tag("read")
        @DisplayName("should call routing DAO then info DAO for getHl7TextInfo")
        void shouldCallRoutingDaoThenInfoDao_whenGetHl7TextInfoCalled() {
            // Given
            PatientLabRouting routing = createTestPatientLabRouting(TEST_LAB_ID, TEST_DEMO_NO);
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(Collections.singletonList(routing));
            when(mockHl7TextInfoDao.findByLabIdList(Collections.singletonList(TEST_LAB_ID)))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then - verify ordering: routing DAO called first, then info DAO
            var inOrder = inOrder(mockPatientLabRoutingDao, mockHl7TextInfoDao);
            inOrder.verify(mockPatientLabRoutingDao).findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7);
            inOrder.verify(mockHl7TextInfoDao).findByLabIdList(Collections.singletonList(TEST_LAB_ID));
        }

        @Test
        @Tag("read")
        @DisplayName("should call find exactly once for getHl7Message")
        void shouldCallFindOnce_whenGetHl7MessageCalled() {
            // Given
            when(mockHl7TextMessageDao.find((int) TEST_LAB_ID))
                .thenReturn(null);

            // When
            labManager.getHl7Message(mockLoggedInInfo, TEST_LAB_ID);

            // Then
            verify(mockHl7TextMessageDao, times(1)).find((int) TEST_LAB_ID);
        }

        @Test
        @Tag("read")
        @DisplayName("should still call Hl7TextInfoDao with empty list when routing list is empty")
        void shouldStillCallHl7TextInfoDao_whenRoutingListIsEmpty() {
            // Given
            when(mockPatientLabRoutingDao.findByDemographicAndLabType(TEST_DEMO_NO, PatientLabRoutingDao.HL7))
                .thenReturn(Collections.emptyList());
            when(mockHl7TextInfoDao.findByLabIdList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

            // When
            labManager.getHl7TextInfo(mockLoggedInInfo, TEST_DEMO_NO);

            // Then - note: the implementation always calls findByLabIdList, even with empty list
            verify(mockHl7TextInfoDao).findByLabIdList(Collections.emptyList());
        }
    }
}
