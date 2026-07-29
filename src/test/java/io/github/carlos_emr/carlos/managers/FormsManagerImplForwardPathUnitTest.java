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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("FormsManagerImpl")
@Tag("unit")
class FormsManagerImplForwardPathUnitTest {

    @Test
    @DisplayName("should URL encode form forward parameters")
    void shouldUrlEncodeFormForwardParameters_whenBuildingForwardPath() {
        String path = FormsManagerImpl.buildFormForwardPath("Annual Review & Care+", "45 6", "12&3");

        assertThat(path).isEqualTo("/form/forwardshortcutname?method=fetch"
                + "&formname=Annual+Review+%26+Care%2B"
                + "&demographic_no=45+6"
                + "&formId=12%263");
    }
}
