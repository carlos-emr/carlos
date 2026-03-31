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
    @DisplayName("should be an Exception")
    void shouldBeException() {
        assertThat(new ShutdownException()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should create with no-arg constructor")
    void shouldCreate_withNoArgConstructor() {
        ShutdownException ex = new ShutdownException();
        assertThat(ex).isNotNull();
    }
}
