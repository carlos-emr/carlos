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
import java.lang.reflect.Field;
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
    @DisplayName("should store permission action subject")
    void shouldStorePermissionAction_subject() throws Exception {
        AccessDeniedException exception = new AccessDeniedException("_rx", "w", 123);

        assertThat(fieldValue(exception, "permission")).isEqualTo("_rx");
        assertThat(fieldValue(exception, "action")).isEqualTo("w");
        assertThat(fieldValue(exception, "subject")).isEqualTo("123");
    }

    private static String fieldValue(AccessDeniedException exception, String fieldName) throws Exception {
        Field field = AccessDeniedException.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(exception);
    }
}
