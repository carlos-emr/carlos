/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


/**
 * Unit tests for {@link WaitListManager}.
 *
 * <p>Tests properties file loading for the legacy wait list configuration.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("manager")
@DisplayName("WaitListManager")
class WaitListManagerUnitTest {

    @Nested
    @DisplayName("waitListProperties")
    class WaitListProperties {

        @Test
        @DisplayName("should load properties file with from_address")
        void shouldLoadProperties_withFromAddress() {
            String fromAddress = WaitListManager.waitListProperties.getProperty("from_address");
            assertThat(fromAddress).isNotNull();
        }

        @Test
        @DisplayName("should have non-empty properties")
        void shouldHaveNonEmptyProperties() {
            assertThat(WaitListManager.waitListProperties).isNotEmpty();
        }
    }

}
