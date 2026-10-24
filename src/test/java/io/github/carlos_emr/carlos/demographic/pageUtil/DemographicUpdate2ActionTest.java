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
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.waitinglist.WaitingListAvailabilityService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
class DemographicUpdate2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    @Mock
    private DemographicDao mockDemographicDao;
    private AutoCloseable mockCloseable;
    private DemographicUpdate2Action action;

    @BeforeEach
    void setUp() throws Exception {
        mockCloseable = MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicUpdate2Action();

        java.lang.reflect.Field secField = DemographicUpdate2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
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
    void shouldRestoreSecurityServiceAndRefuseWrite_whenTransientReferenceIsAbsent() throws Exception {
        java.lang.reflect.Field field = DemographicUpdate2Action.class.getDeclaredField("securityInfoManager");
        field.setAccessible(true);
        field.set(action, null);
        mockRequest.setMethod("POST");
        denyPrivilege("_demographic", "w");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_demographic)");
        verifySecurityCheck("_demographic", "w");
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
     * Issue #5128: Name Used is kept whole in the alias (70) and pref_name (30) holds only what
     * fits. A longer pref_name failed the whole save under a strict sql_mode.
     */
    @Nested
    @DisplayName("Name Used on save")
    class NameUsed {

        private Demographic demographic;

        @BeforeEach
        void setUpPatient() {
            allowPrivilege("_demographic", "w");
            replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
            mockRequest.setMethod("POST");
            addRequestParameter("demographic_no", "123");
            demographic = new Demographic(123);
            when(mockDemographicDao.getDemographic("123")).thenReturn(demographic);
        }

        /** Applies the form, then stops at validation on a too-long province, before any write. */
        private String saveStoppingAtValidation(String nameUsed) throws Exception {
            if (nameUsed != null) {
                addRequestParameter("nameUsed", nameUsed);
            }
            addRequestParameter("province", "X".repeat(Demographic.PROVINCE_MAX_LENGTH + 1));
            return executeAction(action);
        }

        // getPrefName() answers the alias first, so read the value pref_name would be written from.
        private String storedPrefName() throws Exception {
            java.lang.reflect.Field field = Demographic.class.getDeclaredField("prefName");
            field.setAccessible(true);
            return (String) field.get(demographic);
        }

        @Test
        @DisplayName("should keep a 31-character Name Used whole in the alias and its first 30 in pref_name")
        void shouldKeepNameUsedWholeInAlias_whenItIsThirtyOneCharacters() throws Exception {
            String nameUsed = "A".repeat(30) + "B";

            assertThat(saveStoppingAtValidation(nameUsed)).isEqualTo("validationError");

            assertThat(demographic.getAlias()).isEqualTo(nameUsed);
            assertThat(storedPrefName()).isEqualTo("A".repeat(30));
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
            assertThat(errors).containsExactly("Province exceeds maximum length of 20 characters.");
        }

        @Test
        @DisplayName("should keep a 70-character Name Used whole in the alias and its first 30 in pref_name")
        void shouldKeepNameUsedWholeInAlias_whenItIsSeventyCharacters() throws Exception {
            String nameUsed = "C".repeat(Demographic.ALIAS_MAX_LENGTH);

            assertThat(saveStoppingAtValidation(nameUsed)).isEqualTo("validationError");

            assertThat(demographic.getAlias()).isEqualTo(nameUsed);
            assertThat(storedPrefName()).isEqualTo("C".repeat(Demographic.PREF_NAME_MAX_LENGTH));
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
            assertThat(errors).containsExactly("Province exceeds maximum length of 20 characters.");
        }

        @Test
        @DisplayName("should store an empty pref_name when the form sends no Name Used")
        void shouldStoreEmptyPrefName_whenNoNameUsedIsSent() throws Exception {
            assertThat(saveStoppingAtValidation(null)).isEqualTo("validationError");

            assertThat(storedPrefName()).isEmpty();
        }

        @Test
        @DisplayName("should refuse a 71-character Name Used with the form's error and save nothing")
        void shouldReturnValidationError_whenNameUsedExceedsSeventyCharacters() throws Exception {
            addRequestParameter("nameUsed", "D".repeat(Demographic.ALIAS_MAX_LENGTH + 1));

            String result = executeAction(action);

            assertThat(result).isEqualTo("validationError");
            assertThat(mockResponse.getStatus()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
            assertThat(errors).containsExactly("Preferred name exceeds maximum length of 70 characters.");
            verify(mockDemographicDao, never()).save(any(Demographic.class));
        }
    }

    /**
     * Issue #4229: once the Master Record offers waiting lists again, a submitted list
     * must still be one the provider may use, and a refusal must come before any write.
     */
    @Nested
    @DisplayName("waiting-list selection on save")
    class WaitingListSelection {

        @Mock
        private WaitingListAvailabilityService mockWaitingListAvailability;
        private AutoCloseable nestedMocks;
        private final ProviderPreference providerPreference = new ProviderPreference();

        @BeforeEach
        void setUpWaitingList() {
            nestedMocks = MockitoAnnotations.openMocks(this);
            replaceSpringUtilsBean(WaitingListAvailabilityService.class, mockWaitingListAvailability);
            replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
            allowPrivilege("_demographic", "w");
            providerPreference.setMyGroupNo("clinicA");
            setSessionAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, providerPreference);
            mockRequest.setMethod("POST");
            addRequestParameter("demographic_no", "123");
            addRequestParameter("waiting_list_note", "Prefers mornings");
            addRequestParameter("waiting_list_referral_date", "2026-03-04");
            when(mockDemographicDao.getDemographic("123")).thenReturn(new Demographic(123));
        }

        @AfterEach
        void tearDownWaitingList() throws Exception {
            nestedMocks.close();
        }

        @Test
        @DisplayName("should answer 409 before touching the patient when the chosen list is no longer selectable")
        void shouldReturnConflictBeforeAnyWrite_whenChosenListIsNotSelectable() throws Exception {
            addRequestParameter("list_id", "12");
            when(mockWaitingListAvailability.isSelectable(providerPreference, 12)).thenReturn(false);

            String result = executeAction(action);

            assertThat(result).isEqualTo("none");
            assertThat(mockResponse.getStatus()).isEqualTo(409);
            assertThat(mockResponse.getRedirectedUrl()).isNull();
            verifyNoInteractions(mockDemographicDao);
        }

        @Test
        @DisplayName("should let the save continue when the chosen list is selectable")
        void shouldContinueSave_whenChosenListIsSelectable() throws Exception {
            addRequestParameter("list_id", "12");
            // An over-long province stops the save at validation, before any write,
            // which proves the waiting-list check let the request through.
            addRequestParameter("province", "X".repeat(Demographic.PROVINCE_MAX_LENGTH + 1));
            when(mockWaitingListAvailability.isSelectable(providerPreference, 12)).thenReturn(true);

            String result = executeAction(action);

            assertThat(result).isEqualTo("validationError");
            verify(mockWaitingListAvailability).isSelectable(providerPreference, 12);
            verify(mockDemographicDao).getDemographic("123");
        }

        @Test
        @DisplayName("should save demographics only, without consulting lists, when no list is chosen")
        void shouldNotConsultLists_whenNoListIsChosen() throws Exception {
            addRequestParameter("list_id", "0");
            addRequestParameter("province", "X".repeat(Demographic.PROVINCE_MAX_LENGTH + 1));

            String result = executeAction(action);

            assertThat(result).isEqualTo("validationError");
            verifyNoInteractions(mockWaitingListAvailability);
        }

        @Test
        @DisplayName("should refuse a malformed list id as unavailable")
        void shouldReturnConflict_whenListIdIsMalformed() throws Exception {
            addRequestParameter("list_id", "12 OR 1=1");

            String result = executeAction(action);

            assertThat(result).isEqualTo("none");
            assertThat(mockResponse.getStatus()).isEqualTo(409);
            verify(mockWaitingListAvailability).isSelectable(providerPreference, -1);
            verifyNoInteractions(mockDemographicDao);
        }
    }

    @ParameterizedTest
    @CsvSource({"12,12", "0,-1", "-3,-1", "abc,-1", "99999999999,-1"})
    @DisplayName("should parse only positive list ids")
    void shouldParseOnlyPositiveListIds_forSubmittedValues(String submitted, int expected) {
        assertThat(DemographicUpdate2Action.parseListId(submitted)).isEqualTo(expected);
    }
}
