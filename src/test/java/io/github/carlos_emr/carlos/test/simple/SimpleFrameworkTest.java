/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.test.simple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify JUnit 5 framework is working
 */
@DisplayName("Simple Framework Test")
public class SimpleFrameworkTest {

    @Test
    @DisplayName("Test that JUnit 5 is working")
    public void testFrameworkWorks() {
        assertTrue(true, "Basic assertion should work");
        assertEquals(2, 1 + 1, "Math should work");
    }

    @Test
    @DisplayName("Test string operations")
    public void testStringOperations() {
        String hello = "Hello";
        String world = "World";
        String combined = hello + " " + world;
        
        assertEquals("Hello World", combined);
        assertNotNull(combined);
        assertTrue(combined.contains("Hello"));
        assertTrue(combined.contains("World"));
    }

    @Test
    @DisplayName("Test exception handling")
    public void testExceptions() {
        assertThrows(ArithmeticException.class, () -> {
            int result = 10 / 0;
        });
    }
}