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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Regression tests for the BC patient-management same-day unit count across daylight saving
 * changes (issue #6047).
 *
 * <p>{@link ServiceCodeValidationLogic#getCountAvailablePatientManagementUnits} subtracts a prior
 * claim's units from the day's allowance when it was billed on the same service date. The check
 * used to be "fewer than 24 elapsed hours apart", and the spring-forward day has only 23, so a
 * claim on March 8 was charged against a March 9 service. These run in {@code America/Vancouver},
 * where 2026 springs forward on March 8 and falls back on November 1.</p>
 *
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("billing")
@DisplayName("ServiceCodeValidationLogic same-day units across daylight saving changes")
class ServiceCodeValidationLogicSameDayUnitTest extends CarlosUnitTestBase {

    private TimeZone originalDefault;
    private BillingmasterDAO billingmasterDao;

    @BeforeEach
    void setUp() {
        originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefault);
    }

    private static Billingmaster claim(String serviceDate, String units) {
        Billingmaster claim = new Billingmaster();
        claim.setServiceDate(serviceDate);
        claim.setBillingUnit(units);
        return claim;
    }

    private Map<String, Double> availableUnitsFor(String serviceDate, Billingmaster... priorClaims) {
        when(billingmasterDao.findByDemoNoCodeStatusesAndYear(eq(1), any(), anyString()))
                .thenReturn(List.of(priorClaims));
        return new ServiceCodeValidationLogic().getCountAvailablePatientManagementUnits("1", "14015", serviceDate);
    }

    @Test
    @DisplayName("should not charge the spring-forward day's claim against the next day")
    void shouldNotChargeThePreviousDay_whenItWasTheShortSpringForwardDay() {
        Map<String, Double> available = availableUnitsFor("2026-03-09", claim("20260308", "2"));

        assertThat(available.get(ServiceCodeValidationLogic.DAILY_AVAILABLE_UNITS)).isEqualTo(4.0);
        assertThat(available.get(ServiceCodeValidationLogic.ANNUAL_AVAILABLE_UNITS)).isEqualTo(4.0);
    }

    @Test
    @DisplayName("should charge a claim on the same service date, including the fall-back day")
    void shouldChargeTheSameDay_includingTheFallBackDay() {
        assertThat(availableUnitsFor("2026-11-01", claim("20261101", "3"))
                .get(ServiceCodeValidationLogic.DAILY_AVAILABLE_UNITS)).isEqualTo(1.0);
        assertThat(availableUnitsFor("2026-06-15", claim("20260615", "1"))
                .get(ServiceCodeValidationLogic.DAILY_AVAILABLE_UNITS)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("should not charge a claim from the day after the 25-hour fall-back date")
    void shouldNotChargeTheNextDay_afterTheFallBackDate() {
        assertThat(availableUnitsFor("2026-11-01", claim("20261102", "2"))
                .get(ServiceCodeValidationLogic.DAILY_AVAILABLE_UNITS)).isEqualTo(4.0);
    }
}
