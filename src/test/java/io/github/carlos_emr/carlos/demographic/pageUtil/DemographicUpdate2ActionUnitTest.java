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

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
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

    /**
     * The chart's consent section (#3858). Every save re-posts each type's pre-checked radio, so
     * only the separate confirmation box may upgrade an implied record to explicit.
     */
    @Nested
    @DisplayName("saveConsents")
    class SaveConsents {

        private PatientConsentManager consentManager;
        private MockHttpServletRequest request;

        @BeforeEach
        void setUpConsents() {
            consentManager = mock(PatientConsentManager.class);
            ConsentType email = new ConsentType();
            email.setId(7);
            email.setType("email_consent");
            when(consentManager.getActiveConsentTypes()).thenReturn(List.of(email));
            request = new MockHttpServletRequest();
        }

        @Test
        @DisplayName("should record explicit consent only when the box is ticked with Opt-in")
        void shouldRecordExplicitConsent_whenBoxTickedWithOptIn() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).addEditConsentRecord(mockLoggedInInfo, 42, 7, true, false);
            verify(consentManager).recordExplicitConsent(mockLoggedInInfo, 42, 7);
        }

        @Test
        @DisplayName("should not upgrade anything on a routine save with Opt-in pre-checked")
        void shouldNotRecordExplicitConsent_whenBoxNotTicked() {
            request.setParameter("email_consent", "0");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).addEditConsentRecord(mockLoggedInInfo, 42, 7, true, false);
            verify(consentManager, never()).recordExplicitConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should ignore the box when Opt-out is chosen")
        void shouldIgnoreConfirmationBox_whenOptOutChosen() {
            request.setParameter("email_consent", "1");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).addEditConsentRecord(mockLoggedInInfo, 42, 7, true, true);
            verify(consentManager, never()).recordExplicitConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should leave the record unchanged for an unrecognised choice rather than opting in")
        void shouldSkipConsentType_whenChoiceUnrecognised() {
            request.setParameter("email_consent", "yes");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).addEditConsentRecord(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
            verify(consentManager, never()).deleteConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should do nothing with the box when no choice is posted")
        void shouldIgnoreConfirmationBox_whenNoChoicePosted() {
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).addEditConsentRecord(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
            verify(consentManager, never()).recordExplicitConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should accept only the value 1 for the box")
        void shouldIgnoreConfirmationBox_forValueOtherThanOne() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "on");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).recordExplicitConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should not upgrade when the choice is unrecognised even with the box ticked")
        void shouldNotRecordExplicitConsent_whenChoiceUnrecognised() {
            request.setParameter("email_consent", "yes");
            request.setParameter("recordExplicit_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager, never()).recordExplicitConsent(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should log when a requested upgrade was not recorded")
        void shouldLogWarning_whenExplicitConsentNotRecorded() {
            request.setParameter("email_consent", "0");
            request.setParameter("recordExplicit_email_consent", "1");
            when(consentManager.recordExplicitConsent(mockLoggedInInfo, 42, 7)).thenReturn(false);

            try (LogCapture capture = LogCapture.forLogger(DemographicUpdate2Action.class)) {
                DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

                assertThat(capture.messages())
                        .anySatisfy(message -> assertThat(message).contains("explicit consent was requested but not recorded"));
            }
        }

        @Test
        @DisplayName("should delete the consent when cleared and no choice is posted")
        void shouldDeleteConsent_whenCleared() {
            request.setParameter("deleteConsent_email_consent", "1");

            DemographicUpdate2Action.saveConsents(request, mockLoggedInInfo, 42, consentManager);

            verify(consentManager).deleteConsent(mockLoggedInInfo, 42, 7);
            verify(consentManager, never()).addEditConsentRecord(any(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        }
    }
}
