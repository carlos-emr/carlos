/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AccessDeniedException to verify current behavior before implementing JAX-RS exception mappers.
 * These tests ensure the mapper implementation doesn't break existing functionality.
 *
 * @since 2026-02-06
 */
@Tag("unit")
@Tag("rest")
@Tag("regression")
@DisplayName("AccessDeniedException Unit Tests")
class AccessDeniedExceptionUnitTest {

    @Test
    @DisplayName("should store permission, action, and subject when created with string subject")
    void shouldStorePermissionActionSubject_whenCreatedWithStringSubject() throws Exception {
        // Given
        String permission = "_demographic";
        String action = "w";
        String subject = "12345";

        // When
        AccessDeniedException exception = new AccessDeniedException(permission, action, subject);

        // Then
        assertThat(getField(exception, "permission")).isEqualTo(permission);
        assertThat(getField(exception, "action")).isEqualTo(action);
        assertThat(getField(exception, "subject")).isEqualTo(subject);
    }

    @Test
    @DisplayName("should store permission, action, and convert int subject to string")
    void shouldStorePermissionActionAndConvertIntSubject_whenCreatedWithIntSubject() throws Exception {
        // Given
        String permission = "_demographic";
        String action = "r";
        int subject = 12345;

        // When
        AccessDeniedException exception = new AccessDeniedException(permission, action, subject);

        // Then
        assertThat(getField(exception, "permission")).isEqualTo(permission);
        assertThat(getField(exception, "action")).isEqualTo(action);
        assertThat(getField(exception, "subject")).isEqualTo("12345");
    }

    @Test
    @DisplayName("should store permission and action with null subject")
    void shouldStorePermissionAndActionWithNullSubject_whenCreatedWithoutSubject() throws Exception {
        // Given
        String permission = "_admin";
        String action = "r";

        // When
        AccessDeniedException exception = new AccessDeniedException(permission, action);

        // Then
        assertThat(getField(exception, "permission")).isEqualTo(permission);
        assertThat(getField(exception, "action")).isEqualTo(action);
        assertThat(getField(exception, "subject")).isNull();
    }

    /**
     * Helper method to access private fields via reflection.
     */
    private Object getField(AccessDeniedException exception, String fieldName) throws Exception {
        Field field = AccessDeniedException.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(exception);
    }
}
