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
package io.github.carlos_emr.carlos.documentManager.actions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddEditDocument2Action Regression Unit Tests")
@Tag("unit")
@Tag("documentManager")
class AddEditDocument2ActionRegressionUnitTest {

    @Test
    @DisplayName("should append encoded query parameters with equals signs")
    void shouldAppendEncodedQueryParams_whenValuesContainEqualsSigns() {
        StringBuilder redirect = new StringBuilder("/ctx/documentManager/ViewDocumentReport");

        AddEditDocument2Action.appendQueryParam(redirect, "function", "demographic&injected=true");
        AddEditDocument2Action.appendQueryParam(redirect, "curUser", "provider 1");
        AddEditDocument2Action.appendQueryParam(redirect, "appointmentNo", null);

        assertThat(redirect)
                .hasToString("/ctx/documentManager/ViewDocumentReport"
                        + "?function=demographic%26injected%3Dtrue"
                        + "&curUser=provider+1"
                        + "&appointmentNo=");
    }
}
