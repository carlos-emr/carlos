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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import org.mockito.MockedStatic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.ChartConsentOutcome;
import io.github.carlos_emr.carlos.managers.ChartConsentRequest;
import io.github.carlos_emr.carlos.managers.ChartConsentRequest.Choice;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link DemographicUpdate2Action}.
 *
 * <p>Focuses on verifying that this action enforces <strong>write</strong>
 * privilege ({@code "w"}) on {@code _demographic}, unlike the read-only
 * demographic actions that check {@code "r"}.
 *
 * @since 2026-04-04
 */
@DisplayName("DemographicUpdate2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicUpdate2ActionUnitTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    @Mock
    private DemographicDao mockDemographicDao;
    private AutoCloseable mockCloseable;
    private DemographicUpdate2Action action;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicUpdate2Action(mockSecurityInfoManager);

    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockCloseable != null) {
            mockCloseable.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when session is null")
    void shouldThrowSecurityException_whenSessionIsNull() {
        mockRequest.setMethod("POST");
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, null);

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required session");
    }

    @Test
    @DisplayName("should throw SecurityException when user lacks demographic write privilege")
    void shouldThrowSecurityException_whenUserLacksWritePrivilege() {
        mockRequest.setMethod("POST");
        denyPrivilege("_demographic", "w");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_demographic)");

        verifySecurityCheck("_demographic", "w");
    }

    @Test
    @DisplayName("should require write privilege, not read")
    void shouldRequireWritePrivilege_notRead() {
        mockRequest.setMethod("POST");
        // Allow read but deny write
        allowPrivilege("_demographic", "r");
        denyPrivilege("_demographic", "w");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class);

        // Verify it checked for "w", not "r"
        verify(mockSecurityInfoManager).hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), any());
    }

    @Test
    @DisplayName("should return methodNotAllowed when request is GET")
    void shouldReturnMethodNotAllowed_whenRequestIsGet() throws Exception {
        allowPrivilege("_demographic", "w");

        String result = executeAction(action);

        assertThat(result).isEqualTo("methodNotAllowed");
    }

    @Test
    @DisplayName("should normalize middle names when literal null is submitted")
    void shouldNormalizeMiddleNames_whenLiteralNullIsSubmitted() {
        assertThat(DemographicUpdate2Action.normalizeOptionalMiddleNames(" null ")).isEmpty();
        assertThat(DemographicUpdate2Action.normalizeOptionalMiddleNames("Anne Marie"))
                .isEqualTo("Anne Marie");
    }

    @Test
    @DisplayName("should return validationError when province exceeds twenty characters")
    void shouldReturnValidationError_whenProvinceExceedsTwentyCharacters() throws Exception {
        allowPrivilege("_demographic", "w");
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        mockRequest.setMethod("POST");
        addRequestParameter("demographic_no", "123");
        addRequestParameter("province", "X".repeat(Demographic.PROVINCE_MAX_LENGTH + 1));

        Demographic demographic = new Demographic(123);
        when(mockDemographicDao.getDemographic("123")).thenReturn(demographic);

        String result = executeAction(action);

        assertThat(result).isEqualTo("validationError");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        List<String> fieldLengthValidationErrors =
                (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
        assertThat(fieldLengthValidationErrors)
                .contains("Province exceeds maximum length of 20 characters.");
        verify(mockDemographicDao, never()).save(any(Demographic.class));
    }

    @Test
    @DisplayName("should return validationError when last name exceeds thirty characters")
    void shouldReturnValidationError_whenLastNameExceedsThirtyCharacters() throws Exception {
        allowPrivilege("_demographic", "w");
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        mockRequest.setMethod("POST");
        addRequestParameter("demographic_no", "123");
        addRequestParameter("last_name", "X".repeat(Demographic.LAST_NAME_MAX_LENGTH + 1));

        Demographic demographic = new Demographic(123);
        when(mockDemographicDao.getDemographic("123")).thenReturn(demographic);

        String result = executeAction(action);

        assertThat(result).isEqualTo("validationError");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        List<String> fieldLengthValidationErrors =
                (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
        assertThat(fieldLengthValidationErrors)
                .contains("Last name exceeds maximum length of 30 characters.");
        verify(mockDemographicDao, never()).save(any(Demographic.class));
    }

    @Test
    @DisplayName("should save no consent when the form is rejected as a HIN duplicate")
    void shouldNotSaveConsents_whenHinIsDuplicate() throws Exception {
        allowPrivilege("_demographic", "w");
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        PatientConsentManager consentManager = mock(PatientConsentManager.class);
        replaceSpringUtilsBean(PatientConsentManager.class, consentManager);
        io.github.carlos_emr.CarlosProperties properties = io.github.carlos_emr.CarlosProperties.getInstance();
        String originalSetting = properties.getProperty("USE_NEW_PATIENT_CONSENT_MODULE");
        properties.setProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true");
        try {
            mockRequest.setMethod("POST");
            addRequestParameter("demographic_no", "123");
            addRequestParameter("hin", "1234567890");
            addRequestParameter("email_consent", "0");
            addRequestParameter("recordExplicit_email_consent", "1");
            when(mockDemographicDao.getDemographic("123")).thenReturn(new Demographic(123));
            Demographic otherPatient = new Demographic(456);
            otherPatient.setVer("AB");
            when(mockDemographicDao.searchDemographicByHIN(eq("1234567890"), anyInt(), anyInt(), any(), anyBoolean()))
                    .thenReturn(List.of(otherPatient));

            String result = executeAction(action);

            assertThat(result).isEqualTo("duplicate");
            verifyNoInteractions(consentManager);
            verify(mockDemographicDao, never()).save(any(Demographic.class));
        } finally {
            if (originalSetting != null) {
                properties.setProperty("USE_NEW_PATIENT_CONSENT_MODULE", originalSetting);
            } else {
                properties.remove("USE_NEW_PATIENT_CONSENT_MODULE");
            }
        }
    }

    /**
     * The chart's consent section (#3858). Every save re-posts each type's pre-checked radio, so
     * only the separate confirmation box may upgrade an implied record to explicit, and a choice
     * is applied only while the record the page showed still decides. The manager applies the
     * choice in one transaction; these tests pin what the action asks of it.
     */
    @Nested
    @DisplayName("saveConsents")
    class SaveConsents {

        private PatientConsentManager consentManager;
        private MockHttpServletRequest request;
        private ConsentType email;

        @BeforeEach
        void setUpConsents() {
            consentManager = mock(PatientConsentManager.class);
            email = new ConsentType();
            email.setId(7);
            email.setType("email_consent");
            when(consentManager.getActiveConsentTypes()).thenReturn(List.of(email));
            when(consentManager.saveChartConsent(any(), anyInt(), anyInt(), any()))
                    .thenReturn(ChartConsentOutcome.APPLIED);
            request = new MockHttpServletRequest();
        }

        /** A request from a form that does not send the shown record. */
        private ChartConsentRequest unchecked(Choice choice, boolean explicitRequested) {
            return new ChartConsentRequest(choice, explicitRequested, false, null, null);
        }

        @Test
        @DisplayName("should record explicit consent only when the box is ticked with Opt-in")
        void shouldRecordExplicitConsent_whenBoxTickedWithOptIn() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            // One call, so the opt-in and the upgrade are applied together or not at all.
            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.OPT_IN, true));
        }

        @Test
        @DisplayName("should not upgrade anything on a routine save with Opt-in pre-checked")
        void shouldNotRecordExplicitConsent_whenBoxNotTicked() {
            request.setParameter("email_consent", "0");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.OPT_IN, false));
        }

        @Test
        @DisplayName("should ignore the box when Opt-out is chosen")
        void shouldIgnoreConfirmationBox_whenOptOutChosen() {
            request.setParameter("email_consent", "1");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.OPT_OUT, false));
        }

        @Test
        @DisplayName("should leave the record unchanged for an unrecognised choice rather than opting in")
        void shouldSkipConsentType_whenChoiceUnrecognised() {
            request.setParameter("email_consent", "yes");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class)) {
                DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message)
                                .contains("ignoring an unrecognised choice for consent type id 7"))
                        .noneSatisfy(message -> assertThat(message).contains("yes"));
            }
        }

        @Test
        @DisplayName("should apply the picked choice, not a clear, when the radio is picked again after Clear")
        void shouldApplyRadio_whenClearFlagIsStillSet() {
            // Clear sets the hidden flag to 1; picking a radio afterwards leaves it set.
            request.setParameter("email_consent", "0");
            request.setParameter("deleteConsent_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.OPT_IN, false));
        }

        @Test
        @DisplayName("should neither clear nor apply when the radio is unrecognised, whatever the clear flag says")
        void shouldSkipConsentType_whenChoiceUnrecognisedAndClearFlagSet() {
            request.setParameter("email_consent", "yes");
            request.setParameter("deleteConsent_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
        }

        @Test
        @DisplayName("should neither refuse nor audit unreadable shown fields when the choice is unrecognised")
        void shouldNotRefuse_whenChoiceUnrecognisedAndShownFieldsUnreadable() {
            request.setParameter("email_consent", "yes");
            request.setParameter("consentShownId_email_consent", "31-secret");

            try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
                List<ConsentType> refused =
                        DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(refused).isEmpty();
                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                logAction.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("should do nothing with the box when no choice is posted")
        void shouldIgnoreConfirmationBox_whenNoChoicePosted() {
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
        }

        @Test
        @DisplayName("should accept only the value 1 for the box")
        void shouldIgnoreConfirmationBox_forValueOtherThanOne() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "on");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.OPT_IN, false));
        }

        @Test
        @DisplayName("should not upgrade when the choice is unrecognised even with the box ticked")
        void shouldNotRecordExplicitConsent_whenChoiceUnrecognised() {
            request.setParameter("email_consent", "yes");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
        }

        @Test
        @DisplayName("should log when a requested upgrade was not recorded")
        void shouldLogWarning_whenExplicitConsentNotRecorded() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "1");
            when(consentManager.saveChartConsent(any(), anyInt(), anyInt(), any()))
                    .thenReturn(ChartConsentOutcome.EXPLICIT_NOT_RECORDED);

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class)) {
                DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message).contains("explicit consent was requested but not recorded"));
            }
        }

        @Test
        @DisplayName("should log and not delete for an unrecognised clear flag")
        void shouldLogWarning_whenClearFlagUnrecognised() {
            request.setParameter("deleteConsent_email_consent", "2");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class)) {
                DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                assertThat(capture.messages()).anySatisfy(message -> assertThat(message).contains("unrecognised clear flag"));
            }
        }

        @Test
        @DisplayName("should neither delete nor warn for the untouched clear flag 0")
        void shouldIgnoreQuietly_whenClearFlagIsZero() {
            request.setParameter("deleteConsent_email_consent", "0");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class)) {
                DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                assertThat(capture.messages()).noneSatisfy(message -> assertThat(message).contains("unrecognised"));
            }
        }

        @Test
        @DisplayName("should delete the consent when cleared and no choice is posted")
        void shouldDeleteConsent_whenCleared() {
            request.setParameter("deleteConsent_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7, unchecked(Choice.CLEAR, false));
        }

        @Test
        @DisplayName("should pass the shown record's id and choice to the manager")
        void shouldPassShownRecord_toTheManager() {
            request.setParameter("email_consent", "0");
            request.setParameter("consentShownId_email_consent", "31");
            request.setParameter("consentShownChoice_email_consent", "1");

            List<ConsentType> refused = DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7,
                    new ChartConsentRequest(Choice.OPT_IN, false, true, 31, Boolean.TRUE));
            assertThat(refused).isEmpty();
        }

        @Test
        @DisplayName("should pass an empty shown record when the page showed none")
        void shouldPassNoShownRecord_whenPageShowedNone() {
            request.setParameter("email_consent", "1");
            request.setParameter("consentShownId_email_consent", "");
            request.setParameter("consentShownChoice_email_consent", "");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7,
                    new ChartConsentRequest(Choice.OPT_OUT, false, true, null, null));
        }

        @Test
        @DisplayName("should pass the shown record with a clear")
        void shouldPassShownRecord_whenCleared() {
            request.setParameter("deleteConsent_email_consent", "1");
            request.setParameter("consentShownId_email_consent", "31");
            request.setParameter("consentShownChoice_email_consent", "0");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 7,
                    new ChartConsentRequest(Choice.CLEAR, false, true, 31, Boolean.FALSE));
        }

        @Test
        @DisplayName("should refuse the type and log only its id when the shown id is unreadable")
        void shouldRefuseConsentType_whenShownIdIsUnparseable() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "1");
            request.setParameter("consentShownId_email_consent", "31-secret");
            request.setParameter("consentShownChoice_email_consent", "0");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class);
                    MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
                List<ConsentType> refused =
                        DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(refused).containsExactly(email);
                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message)
                                .contains("unreadable shown record for consent type id 7"))
                        .noneSatisfy(message -> assertThat(message).contains("31-secret"));
                // The refusal is on the patient's audit trail, without the value that was sent.
                logAction.verify(() -> LogAction.addLogSynchronous(eq(mockLoggedInInfo),
                        eq("DemographicUpdate2Action.saveConsents"), eq("consent"), isNull(), eq(42),
                        eq(" Demographic: 42 ConsentTypeId: 7 refused: the consent the page showed could not be read")));
            }
        }

        @Test
        @DisplayName("should refuse the type and log only its id when the shown choice is unreadable")
        void shouldRefuseConsentType_whenShownChoiceIsUnparseable() {
            request.setParameter("email_consent", "0");
            request.setParameter("consentShownId_email_consent", "31");
            request.setParameter("consentShownChoice_email_consent", "maybe");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class);
                    MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
                List<ConsentType> refused =
                        DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(refused).containsExactly(email);
                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message)
                                .contains("unreadable shown record for consent type id 7"))
                        .noneSatisfy(message -> assertThat(message).contains("maybe"));
            }
        }

        @Test
        @DisplayName("should refuse the type when the shown choice field is missing but the id was sent")
        void shouldRefuseConsentType_whenChoiceFieldIsMissing() {
            request.setParameter("email_consent", "0");
            request.setParameter("consentShownId_email_consent", "31");

            assertRefusedAsUnreadable();
        }

        @Test
        @DisplayName("should refuse the type when the shown id field is missing but the choice was sent")
        void shouldRefuseConsentType_whenIdFieldIsMissing() {
            request.setParameter("email_consent", "1");
            request.setParameter("consentShownChoice_email_consent", "0");

            assertRefusedAsUnreadable();
        }

        @Test
        @DisplayName("should refuse the type when the shown id is empty but a shown choice was sent")
        void shouldRefuseConsentType_whenShownIdEmptyButChoiceSent() {
            request.setParameter("email_consent", "0");
            request.setParameter("consentShownId_email_consent", "");
            request.setParameter("consentShownChoice_email_consent", "1");

            assertRefusedAsUnreadable();
        }

        @Test
        @DisplayName("should refuse the type when the shown choice is empty but a shown id was sent")
        void shouldRefuseConsentType_whenShownChoiceEmptyButIdSent() {
            request.setParameter("email_consent", "1");
            request.setParameter("consentShownId_email_consent", "31");
            request.setParameter("consentShownChoice_email_consent", " ");

            assertRefusedAsUnreadable();
        }

        /** A shown record posted with only one of its two fields, or one of them empty, is refused like an unreadable one. */
        private void assertRefusedAsUnreadable() {
            try (MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
                List<ConsentType> refused =
                        DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(refused).containsExactly(email);
                verify(consentManager, never()).saveChartConsent(any(), anyInt(), anyInt(), any());
                logAction.verify(() -> LogAction.addLogSynchronous(eq(mockLoggedInInfo),
                        eq("DemographicUpdate2Action.saveConsents"), eq("consent"), isNull(), eq(42),
                        eq(" Demographic: 42 ConsentTypeId: 7 refused: the consent the page showed could not be read")));
            }
        }

        @Test
        @DisplayName("should return the consent types the manager refused as stale, and no others")
        void shouldReturnRefusedConsentTypes_whenManagerReportsStale() {
            ConsentType sms = new ConsentType();
            sms.setId(9);
            sms.setType("sms_consent");
            when(consentManager.getActiveConsentTypes()).thenReturn(List.of(email, sms));
            when(consentManager.saveChartConsent(any(), anyInt(), eq(7), any())).thenReturn(ChartConsentOutcome.STALE);
            request.setParameter("email_consent", "0");
            request.setParameter("consentShownId_email_consent", "31");
            request.setParameter("consentShownChoice_email_consent", "0");
            request.setParameter("sms_consent", "1");
            request.setParameter("consentShownId_sms_consent", "32");
            request.setParameter("consentShownChoice_sms_consent", "1");

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class);
                    MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {
                List<ConsentType> refused =
                        DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(refused).containsExactly(email);
                // The other type is still applied: a refusal is per consent type.
                verify(consentManager).saveChartConsent(mockLoggedInInfo, 42, 9,
                        new ChartConsentRequest(Choice.OPT_OUT, false, true, 32, Boolean.TRUE));
                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message).contains("consent change refused")
                                .contains("consent type id 7"));
            }
        }

        @Test
        @DisplayName("should return nothing refused when every choice was applied")
        void shouldReturnNoRefusedConsentTypes_whenAllApplied() {
            request.setParameter("email_consent", "0");

            assertThat(DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager)).isEmpty();
        }
    }

    /** Where a save redirects to; the chart page reads {@code consentNotSaved} to warn the user. */
    @Nested
    @DisplayName("editRedirectUrl")
    class EditRedirectUrl {

        private ConsentType consentType(int id) {
            ConsentType consentType = new ConsentType();
            consentType.setId(id);
            consentType.setType("type_" + id);
            consentType.setName("Patient-facing name " + id);
            return consentType;
        }

        @Test
        @DisplayName("should carry the refused consent type ids when something was refused")
        void shouldCarryConsentNotSaved_whenConsentWasRefused() {
            String url = DemographicUpdate2Action.editRedirectUrl("/carlos", 42,
                    List.of(consentType(7), consentType(9)));

            assertThat(url).isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=42&consentNotSaved=7,9");
        }

        @Test
        @DisplayName("should not carry consentNotSaved when nothing was refused")
        void shouldNotCarryConsentNotSaved_whenNothingWasRefused() {
            String url = DemographicUpdate2Action.editRedirectUrl("/carlos", 42, List.of());

            assertThat(url).isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=42");
        }
    }
}
