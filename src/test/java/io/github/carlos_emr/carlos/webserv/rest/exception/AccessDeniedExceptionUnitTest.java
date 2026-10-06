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
package io.github.carlos_emr.carlos.webserv.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for access-denied exception state.
 *
 * @since 2026-05-07
 */
@DisplayName("AccessDeniedException regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class AccessDeniedExceptionUnitTest {

    @Test
    @DisplayName("should leave denial context unset for default constructor")
    void shouldLeaveDenialContextUnset_whenConstructedWithNoArguments() {
        AccessDeniedException exception = new AccessDeniedException();

        assertThat(exception.getPermission()).isNull();
        assertThat(exception.getAction()).isNull();
        assertThat(exception.getSubject()).isNull();
    }

    @Test
    @DisplayName("should store permission and action")
    void shouldStorePermissionAndAction_whenConstructedWithoutSubject() {
        AccessDeniedException exception = new AccessDeniedException("_rx", "w");

        assertThat(exception.getPermission()).isEqualTo("_rx");
        assertThat(exception.getAction()).isEqualTo("w");
        assertThat(exception.getSubject()).isNull();
    }

    @Test
    @DisplayName("should store integer subject as string")
    void shouldStoreIntegerSubjectAsString_whenConstructedWithIntSubject() {
        AccessDeniedException exception = new AccessDeniedException("_rx", "w", 123);

        assertThat(exception.getPermission()).isEqualTo("_rx");
        assertThat(exception.getAction()).isEqualTo("w");
        assertThat(exception.getSubject()).isEqualTo("123");
    }

    @Test
    @DisplayName("should store string subject")
    void shouldStoreStringSubject_whenConstructedWithStringSubject() {
        AccessDeniedException exception = new AccessDeniedException("_rx", "w", "abc-123");

        assertThat(exception.getPermission()).isEqualTo("_rx");
        assertThat(exception.getAction()).isEqualTo("w");
        assertThat(exception.getSubject()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("should preserve message without denial context")
    void shouldPreserveMessageWithoutDenialContext_whenConstructedWithMessage() {
        AccessDeniedException exception = new AccessDeniedException("not allowed");

        assertThat(exception).hasMessage("not allowed");
        assertThat(exception.getPermission()).isNull();
        assertThat(exception.getAction()).isNull();
        assertThat(exception.getSubject()).isNull();
    }
}
