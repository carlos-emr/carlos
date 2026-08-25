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
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for demographic add-record field length validation.
 *
 * @since 2026-04-21
 */
@DisplayName("DemographicAddRecord2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicAddRecord2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    @Mock
    private DemographicDao mockDemographicDao;

    private AutoCloseable mockCloseable;
    private DemographicAddRecord2Action action;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicAddRecord2Action(
                mockSecurityInfoManager,
                mockDemographicDao,
                mock(DemographicCustDao.class),
                mock(DemographicExtDao.class),
                mock(DemographicArchiveDao.class),
                mock(DemographicExtArchiveDao.class),
                mock(WaitingListDao.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockCloseable != null) {
            mockCloseable.close();
        }
    }

    @Test
    @DisplayName("should normalize middle names when literal null is submitted")
    void shouldNormalizeMiddleNames_whenLiteralNullIsSubmitted() {
        assertThat(DemographicAddRecord2Action.normalizeOptionalMiddleNames(" null ")).isEmpty();
        assertThat(DemographicAddRecord2Action.normalizeOptionalMiddleNames("Anne Marie"))
                .isEqualTo("Anne Marie");
    }

    @Test
    @DisplayName("should return validationError when year of birth exceeds four characters")
    void shouldReturnValidationError_whenYearOfBirthExceedsFourCharacters() throws Exception {
        allowPrivilege("_demographic", "w");
        mockRequest.setMethod("POST");
        addRequestParameter("last_name", "Valid");
        addRequestParameter("first_name", "Valid");
        addRequestParameter("sex", "F");
        addRequestParameter("year_of_birth", "20255");

        String result = executeAction(action);

        assertThat(result).isEqualTo("validationError");
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        List<String> fieldLengthValidationErrors =
                (List<String>) mockRequest.getAttribute("fieldLengthValidationErrors");
        assertThat(fieldLengthValidationErrors)
                .contains("Year of birth exceeds maximum length of 4 characters.");
        verify(mockDemographicDao, never()).save(any(Demographic.class));
    }

    @Test
    @DisplayName("should return validationError when last name exceeds thirty characters")
    void shouldReturnValidationError_whenLastNameExceedsThirtyCharacters() throws Exception {
        allowPrivilege("_demographic", "w");
        mockRequest.setMethod("POST");
        addRequestParameter("last_name", "X".repeat(Demographic.LAST_NAME_MAX_LENGTH + 1));

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
