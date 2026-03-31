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
 * Unit tests for {@link SessionConstants} session attribute key constants.
 *
 * @since 2026-03-31
 */
@DisplayName("SessionConstants Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class SessionConstantsUnitTest {

    @Test
    @DisplayName("should have non-null LOGGED_IN_PROVIDER constant")
    void shouldHaveLoggedInProviderConstant() {
        assertThat(SessionConstants.LOGGED_IN_PROVIDER).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should have non-null LOGGED_IN_SECURITY constant")
    void shouldHaveLoggedInSecurityConstant() {
        assertThat(SessionConstants.LOGGED_IN_SECURITY).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should have unique constant values")
    void shouldHaveUniqueValues() {
        assertThat(SessionConstants.LOGGED_IN_PROVIDER)
                .isNotEqualTo(SessionConstants.LOGGED_IN_SECURITY);
    }
}
