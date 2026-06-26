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
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
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
}
