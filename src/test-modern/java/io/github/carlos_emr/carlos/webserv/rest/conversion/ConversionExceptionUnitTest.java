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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConversionException to verify current behavior before implementing JAX-RS exception mappers.
 * These tests ensure the mapper implementation doesn't break the catch-and-wrap pattern.
 *
 * @since 2026-02-06
 */
@Tag("unit")
@Tag("rest")
@Tag("regression")
@DisplayName("ConversionException Unit Tests")
class ConversionExceptionUnitTest {

    @Test
    @DisplayName("should preserve message when created with message constructor")
    void shouldPreserveMessage_whenCreatedWithMessageConstructor() {
        // Given
        String expectedMessage = "Conversion failed";

        // When
        ConversionException exception = new ConversionException(expectedMessage);

        // Then
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    @DisplayName("should preserve cause when created with cause constructor")
    void shouldPreserveCause_whenCreatedWithCauseConstructor() {
        // Given
        IllegalArgumentException cause = new IllegalArgumentException("Invalid input");

        // When
        ConversionException exception = new ConversionException(cause);

        // Then
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should preserve both message and cause when created with both")
    void shouldPreserveBothMessageAndCause_whenCreatedWithBoth() {
        // Given
        String expectedMessage = "Conversion error";
        RuntimeException cause = new RuntimeException("Root cause");

        // When
        ConversionException exception = new ConversionException(expectedMessage, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
