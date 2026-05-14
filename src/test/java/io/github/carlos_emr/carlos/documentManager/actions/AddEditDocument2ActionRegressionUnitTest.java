/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
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
    void shouldAppendEncodedQueryParamsWithEqualsSigns() {
        StringBuffer redirect = new StringBuffer("/ctx/documentManager/ViewDocumentReport");

        AddEditDocument2Action.appendQueryParam(redirect, "function", "demographic&injected=true");
        AddEditDocument2Action.appendQueryParam(redirect, "curUser", "provider 1");
        AddEditDocument2Action.appendQueryParam(redirect, "appointmentNo", null);

        assertThat(redirect.toString())
                .isEqualTo("/ctx/documentManager/ViewDocumentReport"
                        + "?function=demographic%26injected%3Dtrue"
                        + "&curUser=provider+1"
                        + "&appointmentNo=");
    }
}
