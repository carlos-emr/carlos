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
package io.github.carlos_emr.carlos.utility;

import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies driver discovery before a database URL has been selected. */
@Tag("unit")
class MariaDbCompatibilityDriverUnitTest {
    @Test
    void shouldReturnNoPropertyDescriptors_withoutAConfiguredUrl() throws Exception {
        var properties = new Properties();
        properties.setProperty("connectTimeout", "1234");
        var expected = (Properties) properties.clone();
        assertThat(new MariaDbCompatibilityDriver().getPropertyInfo(null, properties)).isEmpty();
        assertThat(properties).isEqualTo(expected);
        assertThat(new MariaDbCompatibilityDriver().getPropertyInfo(null, null)).isEmpty();
    }

    @Test
    void shouldDescribeConfiguredTimeout_withALegacyUrl() throws Exception {
        var properties = new Properties();
        properties.setProperty("connectTimeout", "1234");
        var descriptors = new MariaDbCompatibilityDriver().getPropertyInfo("jdbc:mysql://localhost/example", properties);
        assertThat(descriptors).filteredOn(property -> "connectTimeout".equals(property.name))
                .singleElement().extracting(property -> property.value).isEqualTo("1234");
    }
}
