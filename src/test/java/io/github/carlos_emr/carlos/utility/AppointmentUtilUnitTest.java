/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AppointmentUtil} appointment lookup guard behavior.
 *
 * @since 2026-03-31
 */
@DisplayName("AppointmentUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class AppointmentUtilUnitTest {

    @Test
    @DisplayName("should return none for non-empty demographic number")
    void shouldReturnNone_forNonEmptyDemographicNumber() {
        assertThat(AppointmentUtil.getNextAppointment("12345")).isEqualTo("(none)");
    }
}
