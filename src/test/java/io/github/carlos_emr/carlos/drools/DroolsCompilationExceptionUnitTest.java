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
package io.github.carlos_emr.carlos.drools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DroolsCompilationException}.
 *
 * <p>Validates the two-constructor contract of this checked exception that is
 * thrown whenever a DRL rule file fails to compile through the Drools KIE API.
 * The class was introduced during the Drools 2.0 &rarr; 7.74.1 &rarr; 10.0.0 migration to provide
 * a consistent, typed exception for all DRL compilation failures across the subsystems
 * (flowsheets, decision support, prevention, workflow, clinical reports).</p>
 *
 * <p>Because {@code DroolsCompilationException} is a <em>checked</em> exception (extends
 * {@link Exception}, not {@link RuntimeException}), callers are forced to handle DRL
 * compilation failures explicitly. These tests verify that contract is preserved.</p>
 *
 * @see DroolsCompilationException
 * @see DroolsHelper
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("DroolsCompilationException")
class DroolsCompilationExceptionUnitTest {

    /**
     * Verifies that the single-argument constructor preserves the error message.
     * This constructor is used when only the DRL compilation error description is
     * available (e.g., "DRL contained 3 error(s)").
     */
    @Test
    @DisplayName("should preserve message when constructed with message only")
    void shouldPreserveMessage_whenConstructedWithMessageOnly() {
        DroolsCompilationException ex = new DroolsCompilationException("DRL syntax error");

        assertThat(ex.getMessage()).isEqualTo("DRL syntax error");
    }

    /**
     * Verifies that the two-argument constructor preserves both the error message
     * and the root cause. This constructor is used when a DRL compilation failure
     * wraps a lower-level exception (e.g., an {@link java.io.IOException} from
     * reading the DRL resource).
     */
    @Test
    @DisplayName("should preserve cause when constructed with message and cause")
    void shouldPreserveCause_whenConstructedWithMessageAndCause() {
        // Simulate a low-level IO error that the DRL loader would wrap
        RuntimeException cause = new RuntimeException("underlying IO error");

        DroolsCompilationException ex = new DroolsCompilationException("Failed to read DRL", cause);

        // Both message and cause chain must be preserved for diagnostic logging
        assertThat(ex.getMessage()).isEqualTo("Failed to read DRL");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    /**
     * Verifies that the single-argument constructor does not fabricate a cause.
     * When only a message is provided, {@code getCause()} must return {@code null}.
     */
    @Test
    @DisplayName("should return null for cause when constructed with message only")
    void shouldReturnNull_forCauseWhenConstructedWithMessageOnly() {
        DroolsCompilationException ex = new DroolsCompilationException("some error");

        // No cause was provided, so it must be null (not an empty or synthetic exception)
        assertThat(ex.getCause()).isNull();
    }

    /**
     * Verifies that {@code DroolsCompilationException} is a <em>checked</em> exception.
     *
     * <p>This is a deliberate design choice: DRL compilation failures represent
     * recoverable conditions that callers should handle explicitly (e.g., by logging
     * the error and falling back to a cached rule base). Making it a checked exception
     * ensures compile-time enforcement of error handling at every call site.</p>
     */
    @Test
    @DisplayName("should be a checked exception")
    void shouldBeCheckedException() {
        DroolsCompilationException ex = new DroolsCompilationException("test");

        // Must extend Exception (the checked exception hierarchy)
        assertThat(ex).isInstanceOf(Exception.class);
        // Must NOT extend RuntimeException (that would make it unchecked)
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
    }
}
