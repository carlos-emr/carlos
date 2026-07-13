/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for MRI JSP encoding assumptions that must remain safe after refactors. */
@DisplayName("billingONMRI.jsp encoding")
@Tag("unit")
@Tag("billing")
class BillingOnMriJspEncodingUnitTest {

    @Test
    void shouldEncodeDiskId_forJavaScriptAttributeContext() throws Exception {
        String jsp = Files.readString(Path.of(
                "src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONMRI.jsp"));

        assertThat(jsp)
                .doesNotContain("onclick=\"recreate(<c:out value='${row.diskId}'/>)\"")
                .contains("onclick=\"recreate('<carlos:encode value='${row.diskId}' context='javaScriptAttribute'/>')\"");
    }
}
