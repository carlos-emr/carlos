/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.commn.service.FlowSheetCustomizationService.CascadeCheckResult;
import io.github.carlos_emr.carlos.commn.service.FlowSheetCustomizationService.ScopeLevel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlowSheetCustomizationService} scope classification (issue #3962): both
 * scope fields on {@link FlowSheetCustomization} default to null, and a null field must be read
 * as "no provider" / "no patient" rather than as a patient or provider row.
 *
 * @since 2026-10-19
 */
@DisplayName("FlowSheetCustomizationService")
@Tag("unit")
@Tag("service")
class FlowSheetCustomizationServiceUnitTest {

    private FlowSheetCustomizationDao dao;
    private FlowSheetCustomizationService service;

    @BeforeEach
    void setUp() {
        dao = mock(FlowSheetCustomizationDao.class);
        service = new FlowSheetCustomizationService(dao, mock(SecurityInfoManager.class));
    }

    private static FlowSheetCustomization customization(String providerNo, String demographicNo) {
        FlowSheetCustomization cust = new FlowSheetCustomization();
        cust.setProviderNo(providerNo);
        cust.setDemographicNo(demographicNo);
        return cust;
    }

    @Nested
    @DisplayName("getScopeLevel")
    class GetScopeLevel {

        @Test
        void shouldReturnClinic_forEmptyProviderAndZeroDemographic() {
            assertThat(service.getScopeLevel(customization("", "0"))).isEqualTo(ScopeLevel.CLINIC);
        }

        @Test
        void shouldReturnProvider_forProviderAndZeroDemographic() {
            assertThat(service.getScopeLevel(customization("999998", "0"))).isEqualTo(ScopeLevel.PROVIDER);
        }

        @Test
        void shouldReturnPatient_forRealDemographic() {
            assertThat(service.getScopeLevel(customization("999998", "12"))).isEqualTo(ScopeLevel.PATIENT);
            assertThat(service.getScopeLevel(customization("", "12"))).isEqualTo(ScopeLevel.PATIENT);
        }

        @Test
        void shouldReturnClinic_forNullProviderAndZeroDemographic() {
            assertThat(service.getScopeLevel(customization(null, "0"))).isEqualTo(ScopeLevel.CLINIC);
        }

        @Test
        void shouldReturnClinic_forNullProviderAndNullDemographic() {
            assertThat(service.getScopeLevel(customization(null, null))).isEqualTo(ScopeLevel.CLINIC);
        }

        @Test
        void shouldReturnClinic_forEmptyProviderAndNullDemographic() {
            assertThat(service.getScopeLevel(customization("", null))).isEqualTo(ScopeLevel.CLINIC);
        }

        @Test
        void shouldReturnProvider_forProviderAndNullDemographic() {
            assertThat(service.getScopeLevel(customization("999998", null))).isEqualTo(ScopeLevel.PROVIDER);
        }

        @Test
        void shouldReturnPatient_forNullProviderAndRealDemographic() {
            assertThat(service.getScopeLevel(customization(null, "12"))).isEqualTo(ScopeLevel.PATIENT);
        }

        @ParameterizedTest(name = "provider=[{0}] demographic=[{1}] -> {2}")
        @CsvSource(value = {
                "'   ', '0', CLINIC",
                "'', '   ', CLINIC",
                "'   ', '   ', CLINIC",
                "'999998', '   ', PROVIDER",
                "'   ', '12', PATIENT"
        })
        void shouldTreatBlankFieldsLikeUnset_forWhitespaceValues(String providerNo, String demographicNo,
                                                                 ScopeLevel expected) {
            assertThat(service.getScopeLevel(customization(providerNo, demographicNo))).isEqualTo(expected);
        }

        @Test
        void shouldThrowIllegalArgument_whenCustomizationNull() {
            assertThatThrownBy(() -> service.getScopeLevel(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cust");
        }
    }

    @Nested
    @DisplayName("getScopeLevelName")
    class GetScopeLevelName {

        @ParameterizedTest(name = "[{0}] [{1}] -> {2}")
        @CsvSource(value = {
                "'', '0', clinic",
                "NIL, NIL, clinic",
                "'999998', '0', provider",
                "'999998', NIL, provider",
                "'999998', '12', patient",
                "NIL, '12', patient"
        }, nullValues = "NIL")
        void shouldReturnLowercaseName_forEachScope(String providerNo, String demographicNo, String expected) {
            assertThat(service.getScopeLevelName(customization(providerNo, demographicNo))).isEqualTo(expected);
        }

        @Test
        void shouldThrowIllegalArgument_whenCustomizationNull() {
            assertThatThrownBy(() -> service.getScopeLevelName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("cascade blocking with unset fields")
    class CascadeBlocking {

        @Test
        void shouldReportClinicBlockingLevel_whenBlockingRowHasNullFields() {
            FlowSheetCustomization blocking = customization(null, null);
            when(dao.findHigherLevelCustomization("diab", "A1C", "delete", "999998", "12")).thenReturn(blocking);

            CascadeCheckResult result = service.checkCascadingBlocked("diab", "A1C", "delete", "999998", "12");

            assertThat(result.isBlocked()).isTrue();
            assertThat(result.getBlockingLevel()).isEqualTo("clinic");
        }

        @Test
        void shouldBlockArchive_whenClinicRowHasNullFieldsAndCallerIsProvider() {
            FlowSheetCustomization clinicRow = customization(null, null);

            CascadeCheckResult result = service.checkCanArchive(clinicRow, "provider", "999998", null);

            assertThat(result.isBlocked()).isTrue();
        }

        @Test
        void shouldAllowArchive_whenProviderRowHasNullDemographicAndCallerIsProvider() {
            FlowSheetCustomization providerRow = customization("999998", null);

            CascadeCheckResult result = service.checkCanArchive(providerRow, "provider", "999998", null);

            assertThat(result.isBlocked()).isFalse();
        }
    }
}
