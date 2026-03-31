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
 * Unit tests for {@link ShutdownException} application shutdown exception.
 *
 * @since 2026-03-31
 */
@DisplayName("ShutdownException Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class ShutdownExceptionUnitTest {

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        assertThat(new ShutdownException()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("should carry message")
    void shouldCarryMessage() {
        ShutdownException ex = new ShutdownException("Shutting down");
        assertThat(ex.getMessage()).isEqualTo("Shutting down");
    }
}
